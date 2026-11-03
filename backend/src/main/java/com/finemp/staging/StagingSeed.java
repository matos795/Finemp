package com.finemp.staging;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;

/** Explicit one-shot fixture, never a Spring component or application startup hook. */
final class StagingSeed {
    static final String KEY="finemp-synthetic-v1";
    static UUID id(String name) { return UUID.nameUUIDFromBytes((KEY+":"+name).getBytes(StandardCharsets.UTF_8)); }
    static boolean provision(Connection connection,String passwordHash) throws SQLException {
        if(!connection.getAutoCommit() || passwordHash==null || !passwordHash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) throw new IllegalArgumentException("Invalid staging seed context");
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        try {
            execute(connection,"SET LOCAL lock_timeout='10s'");
            execute(connection,"SET LOCAL statement_timeout='30s'");
            execute(connection,"SELECT pg_advisory_xact_lock(534826201)");
            execute(connection,"LOCK TABLE accounting_firms,clients,companies,user_accounts IN SHARE ROW EXCLUSIVE MODE");
            try(var query=connection.prepareStatement("SELECT company_id,user_id FROM staging_seed_receipts WHERE seed_key=?")) {
                query.setString(1,KEY);
                try(var rows=query.executeQuery()) {
                    if(rows.next()) {
                        if(!id("company").equals(rows.getObject(1,UUID.class)) || !id("user").equals(rows.getObject(2,UUID.class))) throw new SQLException("Seed receipt mismatch");
                        require(connection,"SELECT count(*) FROM financial_bank_accounts WHERE id=? AND company_id=?",1,id("bank"),id("company"));
                        require(connection,"SELECT count(*) FROM financial_categories WHERE company_id=? AND id IN (?,?)",2,id("company"),id("income"),id("expense"));
                        connection.commit();return false;
                    }
                }
            }
            for(var table:new String[]{"accounting_firms","clients","companies","user_accounts"})
                require(connection,"SELECT count(*) FROM "+table,0);
            execute(connection,"INSERT INTO accounting_firms(id,name,active) VALUES(?,'Synthetic FINEMP staging',true)",id("firm"));
            execute(connection,"INSERT INTO clients(id,accounting_firm_id,name,active) VALUES(?,?,'Synthetic staging client',true)",id("client"),id("firm"));
            execute(connection,"INSERT INTO companies(id,client_id,legal_name,cnpj) VALUES(?,?,'Synthetic staging company','00000000000000')",id("company"),id("client"));
            execute(connection,"INSERT INTO user_accounts(id,name,email,password_hash,active) VALUES(?,'Synthetic staging operator','demo@finemp.example.invalid',?,true)",id("user"),passwordHash);
            execute(connection,"INSERT INTO company_access_grants(id,user_account_id,company_id,access_role,cost_center_scope) VALUES(?,?,?,'CUSTOM','ALL')",id("grant"),id("user"),id("company"));
            for(var permission:new String[]{"FINANCIAL_ENTRY_MANAGE","BANK_ACCOUNT_VIEW","FINANCIAL_CATEGORY_VIEW"})
                execute(connection,"INSERT INTO company_access_grant_permissions(company_access_grant_id,permission) VALUES(?,?)",id("grant"),permission);
            execute(connection,"INSERT INTO financial_bank_accounts(id,company_id,name,type,opening_balance,opening_date,active) VALUES(?,?,'Synthetic staging cash','CASH',0.00,DATE '2026-01-01',true)",id("bank"),id("company"));
            category(connection,"income","INCOME","1","REC");category(connection,"expense","EXPENSE","2","DADM");
            execute(connection,"INSERT INTO staging_seed_receipts(seed_key,company_id,user_id) VALUES(?,?,?)",KEY,id("company"),id("user"));
            connection.commit();return true;
        } catch(SQLException|RuntimeException ex) {connection.rollback();throw ex;}
        finally {connection.setAutoCommit(true);}
    }
    private static void category(Connection c,String name,String direction,String prefix,String dre) throws SQLException {
        String code=prefix+".1",sort=prefix+"/0000000000000000001";
        execute(c,"INSERT INTO financial_categories(id,company_id,kind,direction,code,sequence_number,sort_key,name,search_name,cash_activity,dre_line) VALUES(?,?,'GROUP',?,?,1,?,? ,?,'OPERATING',?)",
                id(name+"-group"),id("company"),direction,code,sort,"Synthetic "+name,"synthetic "+name,dre);
        execute(c,"INSERT INTO financial_categories(id,company_id,parent_id,kind,direction,code,sequence_number,sort_key,name,search_name) VALUES(?,?,?,'SUBCATEGORY',?,?,1,?,?,?)",
                id(name),id("company"),id(name+"-group"),direction,code+".1",sort+"/0000000000000000001","Synthetic "+name,"synthetic "+name);
    }
    private static void require(Connection c,String sql,long expected,Object...args) throws SQLException {
        try(var statement=c.prepareStatement(sql)) {bind(statement,args);try(var row=statement.executeQuery()){row.next();if(row.getLong(1)!=expected)throw new SQLException("Staging seed precondition failed");}}
    }
    private static void execute(Connection c,String sql,Object...args) throws SQLException {try(var statement=c.prepareStatement(sql)){bind(statement,args);statement.execute();}}
    private static void bind(PreparedStatement statement,Object[] args) throws SQLException {for(int index=0;index<args.length;index++)statement.setObject(index+1,args[index]);}
}
