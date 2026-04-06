package com.pojavmodmanager;

public class ModItem {
    public String name;
    public String description;
    public String requirements;
    public String id;
    public String source;
    public String iconUrl;

    public ModItem(String name, String description, String requirements, String id, String source) {
        this.name = name;
        this.description = description;
        this.requirements = requirements;
        this.id = id;
        this.source = source;
        this.iconUrl = "";
    }
}
