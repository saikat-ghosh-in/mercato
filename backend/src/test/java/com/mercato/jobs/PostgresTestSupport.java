package com.mercato.jobs;

import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.*;
import java.util.*;

/** Defaults to mandatory Testcontainers; explicit local runs may use disposable loopback databases. */
public final class PostgresTestSupport {
    private static final String external=System.getenv("MERCATO_TEST_POSTGRES_URL");
    private static PostgreSQLContainer<?> container;
    private static final List<String> databases=new ArrayList<>();
    private PostgresTestSupport() {}
    public static synchronized String adminUrl() {
        if(external!=null && !external.isBlank()) {
            if(!external.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[a-zA-Z0-9_]+(?:\\?.*)?"))
                throw new IllegalArgumentException("External foundation tests require an explicit loopback database URL");
            return external;
        }
        if(container==null) {container=new PostgreSQLContainer<>("postgres:14.24-alpine");container.start();}
        return container.getJdbcUrl();
    }
    public static String username(){adminUrl();return container==null?Objects.requireNonNullElse(System.getenv("MERCATO_TEST_POSTGRES_USERNAME"),"postgres"):container.getUsername();}
    public static String password(){adminUrl();return container==null?Objects.requireNonNullElse(System.getenv("MERCATO_TEST_POSTGRES_PASSWORD"),""):container.getPassword();}
    public static Connection connect(String url)throws SQLException{return DriverManager.getConnection(url,username(),password());}
    public static synchronized String database() {
        String name="mercato_foundation_"+UUID.randomUUID().toString().replace("-","");
        try(Connection connection=connect(adminUrl());Statement statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE "+name);databases.add(name);
            return adminUrl().replaceFirst("/[^/?]+(?:\\?.*)?$","/"+name);
        } catch(SQLException failure) {throw new IllegalStateException("Unable to create disposable PostgreSQL database",failure);}
    }
    public static synchronized void cleanup() {
        if(databases.isEmpty())return;
        try(Connection connection=connect(adminUrl());Statement statement=connection.createStatement()) {
            for(String name:List.copyOf(databases)) {
                if(!name.matches("mercato_foundation_[a-f0-9]{32}"))throw new IllegalStateException("Unsafe cleanup target");
                statement.execute("DROP DATABASE IF EXISTS "+name+" WITH (FORCE)");databases.remove(name);
            }
        } catch(SQLException failure) {throw new IllegalStateException("Disposable PostgreSQL cleanup failed",failure);}
    }
}
