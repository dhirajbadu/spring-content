package org.springframework.content.jpa.boot;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import org.junit.runner.RunWith;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;

@RunWith(Ginkgo4jRunner.class)
public class ContentJpaDatabaseInitializerTest {

    private ContentJpaDatabaseInitializer initializer;

    private DataSource ds;
    private ResourceLoader resourceLoader;
    private ContentJpaProperties props;

    //mocks
    private Statement stmt;


    {
        Describe("ContentJpaDatabaseInitializer", () -> {
            JustBeforeEach(() -> {
                initializer = new ContentJpaDatabaseInitializer(ds, resourceLoader, props);
            });
            BeforeEach(() -> {
                ds = mock(DataSource.class);
                resourceLoader = new DefaultResourceLoader();
                props = new ContentJpaProperties();
            });
            Context("#initialize", () -> {
                BeforeEach(() -> {
                    Connection conn = mock(Connection.class);
                    when(ds.getConnection()).thenReturn(conn);
                    stmt = mock(Statement.class);
                    when(conn.createStatement()).thenReturn(stmt);
                    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
                    when(conn.getMetaData()).thenReturn(metadata);
                    when(metadata.getDatabaseProductName()).thenReturn("h2");
                });
                Context("when initialization is enabled", () -> {
                    JustBeforeEach(() -> {
                        initializer.initialize();
                    });
                    It("should execute CREATE TABLE statements on the database", () -> {
                        verify(stmt, atLeastOnce()).execute(argThat(containsString("CREATE TABLE")));
                    });
                });
                Context("when initialization is disabled", () -> {
                    BeforeEach(() -> {
                        props.getInitializer().setEnabled(false);
                    });
                    It("should not execute any statements on the database", () -> {
                        verify(stmt, never()).execute(anyString());
                    });
                });
            });
        });
    }
}
