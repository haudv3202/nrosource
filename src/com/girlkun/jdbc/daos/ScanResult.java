/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.girlkun.jdbc.daos;

import java.util.List;

/**
 *
 * @author Mr.VanHau32
 */
public class ScanResult {
    private int totalBan;
    private List<String[]> infoPlayers;

    public ScanResult(int totalBan, List<String[]> infoPlayers) {
        this.totalBan = totalBan;
        this.infoPlayers = infoPlayers;
    }

    public int getTotalBan() {
        return totalBan;
    }

    public List<String[]> getInfoPlayers() {
        return infoPlayers;
    }
}

