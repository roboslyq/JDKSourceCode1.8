package com.roboslyq.datastuct.tree;

/**
 * 资源抽象
 */
public interface Source {
    public void setId(int id);
    public void setName(String name);
    public void setDesc(String desc);
    public int getId();
    public String getName();
    public String getDesc();
}
