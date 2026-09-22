package com.test.voip.entity;

public class Country {

    private String dialCode;
    private String songPath;

    public Country() { }

    public Country(String dialCode, String songPath) {
        this.dialCode = dialCode;
        this.songPath = songPath;
    }

    public String getDialCode() { return dialCode; }
    public void setDialCode(String dialCode) { this.dialCode = dialCode; }

    public String getSongPath() { return songPath; }
    public void setSongPath(String songPath) { this.songPath = songPath; }
}
