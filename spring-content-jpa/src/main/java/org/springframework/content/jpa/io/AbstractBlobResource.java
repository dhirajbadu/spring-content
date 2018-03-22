package org.springframework.content.jpa.io;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.content.commons.io.FileRemover;
import org.springframework.content.commons.io.ObservableInputStream;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.sql.*;

public abstract class AbstractBlobResource implements BlobResource {

    private static Log logger = LogFactory.getLog(AbstractBlobResource.class);

    private Object id;
    private JdbcTemplate template;
    private PlatformTransactionManager txnMgr;

    public AbstractBlobResource(Object id, JdbcTemplate template, PlatformTransactionManager txnMgr) {
        this.id = id;
        this.template = template;
        this.txnMgr = txnMgr;
    }

    public Object getId() {
        synchronized (id) {
            return id;
        }
    }

    protected void setId(Object id) {
        synchronized (id) {
            this.id = id;
        }
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final Object id = this.id;
        final AbstractBlobResource resource = this;
        TransactionTemplate txn = new TransactionTemplate(txnMgr);

        PipedInputStream is = new PipedInputStream();
        PipedOutputStream os = new PipedOutputStream(is);

        Thread t = new Thread(
                () -> {
                    try {
                        Object rc = update(txn, is, id, resource);
                        if (rc != null && !rc.equals(-1)) {
                            resource.setId(rc);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
        );
        t.start();

//        File tempFile = File.createTempFile("_sc_jpa_generic_", null);
//        FileOutputStream fos = new FileOutputStream(tempFile);
//        InputStream fin = new FileInputStream(tempFile);
//
//        ObservableOutputStream os = new ObservableOutputStream(fos);
//        os.addObservers(new OutputStreamObserver() {
//            @Override
//            public void closed() {
//                try {
//                    update(txn, fin, id, resource);
//                } catch (SQLException e) {
//                    e.printStackTrace();
//                }
//            }
//        },
//        new FileRemover(tempFile));

        return os;
//    }
    }

    private Object update(TransactionTemplate txn, InputStream fin, Object id, AbstractBlobResource resource) throws SQLException {
        return txn.execute(new TransactionCallback<Integer>() {
            @Override
            public Integer doInTransaction(TransactionStatus transactionStatus) {
                Integer rc = null;
                if (exists()) {
                    String sql = "UPDATE BLOBS SET content=? WHERE id=?";
                    rc = template.execute(sql, new PreparedStatementCallback<Integer>() {
                        @Override
                        public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                            // mark the resource as being updated
                            resource.setId(-1);

                            ps.setBinaryStream(1, fin);
                            ps.setInt(2, Integer.parseInt(id.toString()));
                            ps.executeUpdate();
                            IOUtils.closeQuietly(fin);
                            return Integer.parseInt(id.toString());
                        }
                    });
                } else {
                    String sql = "INSERT INTO BLOBS (content) VALUES(?)";
                    rc = template.execute(
                        new PreparedStatementCreator() {
                            @Override
                            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                                return con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                            }
                        }, new PreparedStatementCallback<Integer>() {
                            @Override
                            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                                ResultSet set = null;
                                try {
                                    ps.setBinaryStream(1, fin);
                                    ps.executeUpdate();
                                    IOUtils.closeQuietly(fin);
                                    set = ps.getGeneratedKeys();
                                    set.next();
                                    return set.getInt(1);
                                } catch (SQLException sqle) {
                                    logger.error("inserting content", sqle);
                                } finally {
                                    if (set != null) {
                                        try {
                                            set.close();
                                        } catch (SQLException e) {
                                            logger.error("closing insert content set", e);
                                        }
                                    }
                                }
                                return null;
                            }

                        }
                    );
                }
                return rc;
            }
        });
    }

    @Override
    public boolean exists() {
        String sql = "SELECT COUNT(id) FROM BLOBS WHERE id=" + this.id;
        return this.template.query(sql, new ResultSetExtractor<Boolean>() {
            @Override
            public Boolean extractData(ResultSet rs) throws SQLException, DataAccessException {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count == 1;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public URL getURL() throws IOException {
        return null;
    }

    @Override
    public URI getURI() throws IOException {
        return null;
    }

    @Override
    public File getFile() throws IOException {
        return null;
    }

    @Override
    public long contentLength() throws IOException {
        return 0;
    }

    @Override
    public long lastModified() throws IOException {
        return 0;
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        return null;
    }

    @Override
    public String getFilename() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final Object id = this.id;
        String sql = "SELECT content FROM BLOBS WHERE id=" + this.id;
        return this.template.query(sql, new ResultSetExtractor<InputStream>() {
            @Override
            public InputStream extractData(ResultSet rs) throws SQLException, DataAccessException {
                if(!rs.next()) return null;

                try {
                    File tempFile = File.createTempFile("_sc_jpa_generic_", null);
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    InputStream is = rs.getBinaryStream(1);
                    try {
                        IOUtils.copyLarge(is, fos);
                    } finally {
                        IOUtils.closeQuietly(is);
                        IOUtils.closeQuietly(fos);
                    }
                    return new ObservableInputStream(new FileInputStream(tempFile), new FileRemover(tempFile));
                } catch (IOException ioe) {
                    logger.error(String.format("getting input stream for content %s", id), ioe);
                    return null;
                }
            }
        });
    }

    @Override
    public void delete() {
        final Object id = this.id;
        String sql = "DELETE FROM BLOBS WHERE id=" + this.id;
        this.template.update(sql);
    }
}
