package com.finemp.finance.center;

public record ResultCenterCode(String value) {
    public ResultCenterCode {
        if(value==null || value.length()>100)throw invalid();
        value=value.replaceAll("(?U)\\s","");
        if(value.length()>20 || !value.matches("[0-9]+(\\.[0-9]+)*"))throw invalid();
    }
    public String parentCode() {
        int separator=value.lastIndexOf('.');
        return separator<0?null:value.substring(0,separator);
    }
    public boolean isWithin(ResultCenterCode parent) {
        return value.equals(parent.value()) || value.startsWith(parent.value()+".");
    }
    public String sortKey() {
        return java.util.Arrays.stream(value.split("\\.")).map(segment->{
            String numeric=new java.math.BigInteger(segment).toString();
            return "0".repeat(20-numeric.length())+numeric+"!"+segment;
        }).collect(java.util.stream.Collectors.joining("/"));
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Código de centro de resultado inválido.");}
}
