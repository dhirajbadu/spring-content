package internal.org.springframework.content.jpa.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import org.apache.commons.io.IOUtils;
import org.junit.runner.RunWith;

import org.springframework.content.jpa.io.AbstractBlobResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.JustBeforeEach;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

@RunWith(Ginkgo4jRunner.class)
public class GenericBlobResourceTest {

	private GenericBlobResource resource;

	private String id;
	private JdbcTemplate template;
	private PlatformTransactionManager txnMgr;

	private DataSource ds;
	private Connection conn;
	private Statement statement;
	private PreparedStatement preparedStatement;
	private ResultSet rs;

	private InputStream in;

	private Object result;

	{
		Describe("GenericBlobResource", () -> {
			BeforeEach(() -> {
				ds = mock(DataSource.class);
				template = new JdbcTemplate(ds);
				txnMgr = new DataSourceTransactionManager(ds);
			});
			Context("#exists", () -> {
				BeforeEach(() -> {
					conn = mock(Connection.class);
					statement = mock(Statement.class);
					rs = mock(ResultSet.class);

					when(ds.getConnection()).thenReturn(conn);
					when(conn.createStatement()).thenReturn(statement);
					when(statement.executeQuery(anyObject())).thenReturn(rs);
				});
				JustBeforeEach(() -> {
					resource = new GenericBlobResource(id, template, txnMgr);
					result = resource.exists();
				});
				Context("given the blob exists in the database", () -> {
					BeforeEach(() -> {
						when(rs.next()).thenReturn(true);
						when(rs.getInt(1)).thenReturn(1);
					});
					It("should return true", () -> {
						assertThat(result, is(true));
					});
				});
				Context("given the blob does not exist in the database", () -> {
					BeforeEach(() -> {
						when(rs.next()).thenReturn(true);
						when(rs.getInt(1)).thenReturn(0);
					});
					It("should return false", () -> {
						assertThat(result, is(false));
					});
				});
				Context("given no blobs exist in the database", () -> {
					BeforeEach(() -> {
						when(rs.next()).thenReturn(false);
					});
					It("should return false", () -> {
						assertThat(result, is(false));
					});
				});
			});
			Context("#getInputStream", () -> {
				BeforeEach(() -> {
					conn = mock(Connection.class);
					statement = mock(Statement.class);
					rs = mock(ResultSet.class);

					when(ds.getConnection()).thenReturn(conn);
					when(conn.createStatement()).thenReturn(statement);
					when(statement.executeQuery(anyObject())).thenReturn(rs);
				});
				JustBeforeEach(() -> {
					resource = new GenericBlobResource(id, template, txnMgr);
					result = resource.getInputStream();
				});
				Context("given the blob exists in the database", () -> {
					BeforeEach(() -> {
						when(rs.next()).thenReturn(true);
						Blob blob = mock(Blob.class);
						when(rs.getBlob(2)).thenReturn(blob);
						when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(
								"Hello Spring Content PostgreSQL BLOBby world!"
										.getBytes()));
					});
					It("should be an ObservableInputStream with a file remover", () -> {
						assertThat(result, instanceOf(AbstractBlobResource.ClosingInputStream.class));
					});
					It("should return the correct content", () -> {
						InputStream expected = null;
						try {
							expected = new ByteArrayInputStream(
									"Hello Spring Content PostgreSQL BLOBby world!"
											.getBytes());
							assertThat(
									IOUtils.contentEquals(expected, (InputStream) result),
									is(true));
						}
						finally {
							IOUtils.closeQuietly(expected);
							IOUtils.closeQuietly((InputStream) result);
						}
					});
				});
				Context("given the blob does not exist in the database", () -> {
					BeforeEach(() -> {
						when(rs.next()).thenReturn(false);
					});
					It("should return null", () -> {
						assertThat(result, is(nullValue()));

						verify(rs).close();
						verify(statement).close();
						verify(conn).close();
					});
				});
			});
			Context("#getOutputStream", () -> {
				BeforeEach(() -> {
					conn = mock(Connection.class);
					statement = mock(Statement.class);
					rs = mock(ResultSet.class);

					when(ds.getConnection()).thenReturn(conn);
					when(conn.createStatement()).thenReturn(statement);
					when(statement.executeQuery(anyObject())).thenReturn(rs);
				});
				JustBeforeEach(() -> {
					id = "999";
					resource = new GenericBlobResource(id, template, txnMgr);
					result = resource.getOutputStream();
				});
				Context("given the blob exists in the database", () -> {
					BeforeEach(() -> {
						// exists
						when(rs.next()).thenReturn(true);
						when(rs.getInt(1)).thenReturn(1);

						in = new ByteArrayInputStream(
								"Hello Spring Content JPA PostreSQL World!".getBytes());

						// update
						preparedStatement = mock(PreparedStatement.class);
						when(conn.prepareStatement(anyString()))
								.thenReturn(preparedStatement);
					});
					JustBeforeEach(() -> {
						IOUtils.copy(in, (OutputStream) result);

						IOUtils.closeQuietly(in);
						IOUtils.closeQuietly((OutputStream) result);
					});
					It("should use update to overwrite the content", () -> {
						verify(conn, timeout(100)).prepareStatement(
								argThat(containsString("UPDATE BLOBS")));

						verify(preparedStatement, timeout(100)).setBlob(eq(1),(InputStream)argThat(is(instanceOf(InputStream.class))));
						verify(preparedStatement, timeout(100)).setString(2, "999");
						verify(preparedStatement, timeout(100)).executeUpdate();
					});
				});
				Context("given the blob does not exist in the database", () -> {
					BeforeEach(() -> {
						// exists
						when(rs.next()).thenReturn(true);
						when(rs.getInt(1)).thenReturn(0);

						in = new ByteArrayInputStream(
								"Hello Spring Content JPA PostgreSQL World!".getBytes());

						// insert
						preparedStatement = mock(PreparedStatement.class);
						when(conn.prepareStatement(anyString(), anyInt()))
								.thenReturn(preparedStatement);

						// generated keys
						ResultSet generatedKeys = mock(ResultSet.class);
						when(preparedStatement.getGeneratedKeys())
								.thenReturn(generatedKeys);
					});
					JustBeforeEach(() -> {
						IOUtils.copy(in, (OutputStream) result);

						IOUtils.closeQuietly(in);
						IOUtils.closeQuietly((OutputStream) result);
					});
					It("should use insert to add the content", () -> {
						verify(conn, timeout(100)).prepareStatement(
								argThat(containsString("INSERT INTO BLOBS")),
								eq(Statement.RETURN_GENERATED_KEYS));
						verify(preparedStatement, timeout(100)).setString(eq(1),argThat(is("999")));
						verify(preparedStatement, timeout(100)).setBlob(eq(2),(InputStream)argThat(is(instanceOf(InputStream.class))));
						verify(preparedStatement, timeout(100)).executeUpdate();

						assertThat(resource.getId(), is("999"));
					});
					It("should update the ID of the resource from the ID returned by the database",
							() -> {
								while (resource.getId().equals("999") == false) {
									Thread.sleep(100);
								}
								assertThat(resource.getId(), is("999"));
							});
				});
			});
		});
	}
}
