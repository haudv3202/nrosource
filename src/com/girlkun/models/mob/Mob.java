package com.girlkun.models.mob;

import com.girlkun.consts.ConstMap;
import com.girlkun.consts.ConstMob;
import com.girlkun.consts.ConstTask;
import com.girlkun.models.Template;
import com.girlkun.models.item.Item;
import com.girlkun.models.map.ItemMap;

import java.util.List;

import com.girlkun.models.map.Zone;
import com.girlkun.models.player.Location;
import com.girlkun.models.player.Pet;
import com.girlkun.models.player.Player;
import com.girlkun.models.reward.ItemMobReward;
import com.girlkun.models.reward.MobReward;
import com.girlkun.models.skill.PlayerSkill;
import com.girlkun.models.skill.Skill;
import com.girlkun.network.io.Message;
import com.girlkun.server.Maintenance;
import com.girlkun.server.Manager;
import com.girlkun.server.ServerManager;
import com.girlkun.services.*;
import com.girlkun.utils.Util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;

public class Mob {

    public int id;
    public Zone zone;
    public int tempId;
    public String name;
    public byte level;

    public MobPoint point;
    public MobEffectSkill effectSkill;
    public Location location;

    public byte pDame;
    public int pTiemNang;
    private long maxTiemNang;

    public long lastTimeDie;
    public int lvMob = 0;
    public int status = 5;
    public short cx;
    public short cy;
    public int action = 0;
    public static int TIME_START_HIRU = 23;

    public Mob(Mob mob) {
        this.point = new MobPoint(this);
        this.effectSkill = new MobEffectSkill(this);
        this.location = new Location();
        this.id = mob.id;
        this.tempId = mob.tempId;
        this.level = mob.level;
        this.point.setHpFull(mob.point.getHpFull());
        this.point.sethp(this.point.getHpFull());
        this.location.x = mob.location.x;
        this.location.y = mob.location.y;
        this.pDame = mob.pDame;
        this.pTiemNang = mob.pTiemNang;
        this.setTiemNang();
    }

    public Mob() {
        this.point = new MobPoint(this);
        this.effectSkill = new MobEffectSkill(this);
        this.location = new Location();
    }

    public static void initMobBanDoKhoBau(Mob mob, byte level) {
        mob.point.dame = level * 3250 * mob.level * 4;
        mob.point.maxHp = level * 12472 * mob.level * 2 + level * 7263 * mob.tempId;
    }

    public static void initMobKhiGaHuyDiet(Mob mob, byte level) {
        mob.point.dame = level * 3250 * mob.level * 4;
        mob.point.maxHp = level * 12472 * mob.level * 2 + level * 7263 * mob.tempId;
    }

    public static void initMobConDuongRanDoc(Mob mob, byte level) {
        mob.point.dame = level * 3250 * mob.level * 4;
        mob.point.maxHp = level * 12472 * mob.level * 2 + level * 7263 * mob.tempId;
    }

    public boolean isSieuQuai() {
        return this.lvMob > 0;
    }

    public synchronized void injured(Player plAtt, int damage, boolean dieWhenHpFull) {
        if (!this.isDie()) {
            if (damage >= this.point.hp) {
                damage = (int) this.point.hp;
            }
            if (this.zone.map.mapId == 112) {
                plAtt.NguHanhSonPoint++;
            }
            if (!dieWhenHpFull) {
                if (this.point.hp == this.point.maxHp && damage >= this.point.hp) {
                    damage = (int) (this.point.hp - 1);
                }
                if (this.tempId == 0 && damage > 10) {
                    damage = 10;
                }
            }
            if (plAtt != null) {
                switch (plAtt.playerSkill.skillSelect.template.id) {
                    case Skill.KAMEJOKO:
                    case Skill.MASENKO:
                    case Skill.ANTOMIC:
                        if (plAtt.nPoint.multicationChuong > 0 && Util.canDoWithTime(plAtt.nPoint.lastTimeMultiChuong, PlayerSkill.TIME_MUTIL_CHUONG)) {
                            damage *= plAtt.nPoint.multicationChuong;
                            plAtt.nPoint.lastTimeMultiChuong = System.currentTimeMillis();
                        }

                }
            }
            this.point.hp -= damage;

            if (this.isDie()) {
                this.lvMob = 0;
                this.status = 0;
                this.sendMobDieAffterAttacked(plAtt, damage);
                TaskService.gI().checkDoneTaskKillMob(plAtt, this);
                TaskService.gI().checkDoneSideTaskKillMob(plAtt, this);
                this.lastTimeDie = System.currentTimeMillis();
                if (this.id == 13) {
                    this.zone.isbulon13Alive = false;
                }
                if (this.id == 14) {
                    this.zone.isbulon14Alive = false;
                }
                if (this.isSieuQuai()) {
                    plAtt.achievement.plusCount(12);
                }
            } else {
                this.sendMobStillAliveAffterAttacked(damage, plAtt != null ? plAtt.nPoint.isCrit : false);
            }
            if (plAtt != null) {
                Service.gI().addSMTN(plAtt, (byte) 2, getTiemNangForPlayer(plAtt, damage), true);
            }
        }
    }

    public static void hoiSinhMob(Mob mob) {
        mob.point.hp = mob.point.maxHp;
        mob.setTiemNang();
        Message msg;
        try {
            msg = new Message(-13);
            msg.writer().writeByte(mob.id);
            msg.writer().writeByte(mob.tempId);
            msg.writer().writeByte(0); //level mob
            msg.writer().writeInt(Util.TamkjllGH(mob.point.hp));
            Service.getInstance().sendMessAllPlayerInMap(mob.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void setTiemNang() {
        this.maxTiemNang = this.point.getHpFull() * (this.pTiemNang + Util.nextInt(-2, 2)) / 100;
    }

    private long lastTimeAttackPlayer;

    public boolean isDie() {
        return this.point.gethp() <= 0;
    }

    public synchronized void injured(Player plAtt, float damage, boolean dieWhenHpFull) {
        if (!this.isDie()) {
            if (damage >= this.point.hp) {
                damage = this.point.hp;
            }
            if (!dieWhenHpFull) {
                if (this.point.hp == this.point.maxHp && damage >= this.point.hp) {
                    damage = this.point.hp - 1;
                }
                if (this.tempId == 0 && damage > 10) {
                    damage = 10;
                }
            }
            this.point.hp -= damage;
            if (this.isDie()) {
                this.status = 0;
                this.sendMobDieAffterAttacked(plAtt, Util.TamkjllGH(damage));
                TaskService.gI().checkDoneTaskKillMob(plAtt, this);
                TaskService.gI().checkDoneSideTaskKillMob(plAtt, this);
                this.lastTimeDie = System.currentTimeMillis();
            } else {
                this.sendMobStillAliveAffterAttacked(Util.TamkjllGH(damage), plAtt != null ? plAtt.nPoint.isCrit : false);
            }
            if (plAtt != null) {
                Service.getInstance().addSMTN(plAtt, (byte) 2, getTiemNangForPlayer(plAtt, Util.TamkjllGH(damage)), true);
            }
        }
    }

    public long getTiemNangForPlayer(Player pl, long dame) {
        int levelPlayer = Service.gI().getCurrLevel(pl);
        int n = levelPlayer - this.level;
        long pDameHit = dame * 100 / point.getHpFull();
        long tiemNang = pDameHit * maxTiemNang / 100;
        if (tiemNang <= 0) {
            tiemNang = 1;
        }
        if (n >= 0) {
            for (int i = 0; i < n; i++) {
                long sub = tiemNang * 10 / 100;
                if (sub <= 0) {
                    sub = 1;
                }
                tiemNang -= sub;
            }
        } else {
            for (int i = 0; i < -n; i++) {
                long add = tiemNang * 10 / 100;
                if (add <= 0) {
                    add = 1;
                }
                tiemNang += add;
            }
        }
        if (tiemNang <= 0) {
            tiemNang = 1;
        }
        tiemNang = (int) pl.nPoint.calSucManhTiemNang(tiemNang);
        if (pl.zone.map.mapId == 122 || pl.zone.map.mapId == 123 || pl.zone.map.mapId == 124) {
            tiemNang *= 20;
        }
        return tiemNang;
    }

    public void update() {
        if (isDie() && this.tempId == 70 && (System.currentTimeMillis() - lastTimeDie) > 5000 && level <= 2) {
            if (level == 0) {
                level = 1;
                action = 6;
                this.point.hp = this.point.maxHp;
            } else if (level == 1) {
                level = 2;
                action = 5;
                this.point.hp = this.point.maxHp;
            } else if (level == 2) {
                level = 3;
                action = 9;
            }
            int trai = 0;
            int phai = 1;
            int next = 0;
//            ItemMap GOLD = new ItemMap(this.zone, 190, Util.nextInt(10000, 20000), this.location.x, this.location.y, -1);
//            for(int i = 0; i < 30; i++){
//                int X = next == 0 ? -5 * trai : 5 * phai;
//                if(next == 0){
//                    trai++;
//                }
//                else{
//                    phai++;
//                }
//                next = next == 0 ? 1 : 0;
//                if(trai > 10){
//                    trai = 0;
//                }
//                if(phai > 10){
//                    phai = 1;
//                }
//                Service.gI().dropItemMap(zone, GOLD);
//            }
            Service.gI().sendBigBoss2(zone, action, this);
            if (level <= 2) {
                Message msg = null;
                try {
                    msg = new Message(-9);
                    msg.writer().writeByte(this.id);
                    msg.writer().writeInt((int) this.point.hp);
                    msg.writer().writeInt(1);
                    Service.gI().sendMessAllPlayerInMap(zone, msg);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                        msg = null;
                    }
                }
            } else {
                cx = -1000;
                cy = -1000;
            }
        }

        if (this.isDie() && !Maintenance.isRuning) {
            switch (zone.map.type) {
                case ConstMap.MAP_DOANH_TRAI:
                    break;
                case ConstMap.MAP_BAN_DO_KHO_BAU:
                    break;
                case ConstMap.MAP_KHI_GA_HUY_DIET:
                    break;
                case ConstMap.MAP_CON_DUONG_RAN_DOC:
                    break;
                case ConstMap.MAP_HIRU:
                    if (Util.canDoWithTime(this.lastTimeDie, 5000) && this.tempId == 70 && !Util.isTimeHiru()) {
                        this.level = 0;
                        this.hoiSinh();
                        this.sendMobHoiSinh();
                    } else if (this.tempId == 70 && this.isDie() && this.level == 3) {
                        this.zone.mobs.remove(0);
                    }
                    break;
                default:
                    if (Util.canDoWithTime(lastTimeDie, 5000)) {
                        if (this.tempId == 77) {
                            long currentTime = System.currentTimeMillis();
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(currentTime);
                            cal.set(Calendar.HOUR_OF_DAY, 20); // Đặt giờ hồi sinh là 20:00
                            cal.set(Calendar.MINUTE, 0);
                            cal.set(Calendar.SECOND, 0);
                            long respawnTime = cal.getTimeInMillis();

                            // Kiểm tra nếu đã đến thời gian hồi sinh
                            if (currentTime >= respawnTime) {
                                this.sendMobHoiSinh();
                            }
                        } else {
                            this.hoiSinh();
                            this.sendMobHoiSinh();
                        }
                    }
            }
        }
        effectSkill.update();
        if (this.tempId == 70) {
            BigbossAttack();
        } else {
            attackPlayer();
        }
    }

    private void attackPlayer() {
        if (!isDie() && !effectSkill.isHaveEffectSkill() && !(tempId == 0) && Util.canDoWithTime(lastTimeAttackPlayer, 2000)) {
            Player pl = getPlayerCanAttack();
            if (pl != null) {
                this.mobAttackPlayer(pl);
            }
            this.lastTimeAttackPlayer = System.currentTimeMillis();
        }
    }

    private Player getPlayerCanAttack() {
        int distance = 100;
        Player plAttack = null;
        try {
            List<Player> players = this.zone.getNotBosses();
            for (Player pl : players) {
                if (!pl.isDie() && !pl.isNewPet && !pl.name.equals("Jajirô") && !pl.isBoss && !pl.effectSkin.isVoHinh) {
                    int dis = Util.getDistance(pl, this);
                    if (dis <= distance) {
                        plAttack = pl;
                        distance = dis;
                    }
                }
            }
        } catch (Exception e) {

        }
        return plAttack;
    }

    //**************************************************************************
    private void mobAttackPlayer(Player player) {
        int dameMob = (int) this.point.getDameAttack();
        if (player.charms.tdDaTrau > System.currentTimeMillis()) {
            dameMob /= 2;
        }
        int dame = (int) player.injured(null, dameMob, false, true);
        this.sendMobAttackMe(player, dame);
        this.sendMobAttackPlayer(player);
    }

    private void sendMobAttackMe(Player player, int dame) {
        if (!player.isPet) {
            Message msg;
            try {
                msg = new Message(-11);
                msg.writer().writeByte(this.id);
                msg.writer().writeInt(dame); //dame
                player.sendMessage(msg);
                msg.cleanup();
            } catch (Exception e) {
            }
        }
    }

    private void sendMobAttackPlayer(Player player) {
        Message msg;
        try {
            msg = new Message(-10);
            msg.writer().writeByte(this.id);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeInt((int) player.nPoint.hp);
            Service.gI().sendMessAnotherNotMeInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public void hoiSinh() {
        this.status = 5;
        this.point.hp = this.point.maxHp;
        this.setTiemNang();
    }

    public void sendMobHoiSinh() {
        Message msg;
        try {
            msg = new Message(-13);
            msg.writer().writeByte(this.id);
            msg.writer().writeByte(this.tempId);
            msg.writer().writeByte(lvMob);
            msg.writer().writeInt(Util.TamkjllGH(this.point.hp));
            Service.getInstance().sendMessAllPlayerInMap(this.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    //**************************************************************************
    private void sendMobDieAffterAttacked(Player plKill, int dameHit) {
        Message msg;
        try {
            msg = new Message(-12);
            msg.writer().writeByte(this.id);
            msg.writer().writeInt(dameHit);
            msg.writer().writeBoolean(plKill.nPoint.isCrit); // crit
            List<ItemMap> items = mobReward(plKill, this.dropItemTask(plKill), msg);
            Service.getInstance().sendMessAllPlayerInMap(this.zone, msg);
            msg.cleanup();
            hutItem(plKill, items);
        } catch (Exception e) {
        }
    }

    private void hutItem(Player player, List<ItemMap> items) {
        if (!player.isPet && !player.isNewPet) {
            if (player.charms.tdThuHut > System.currentTimeMillis()) {
                for (ItemMap item : items) {
                    if (item.itemTemplate.id != 590) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 220) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 221) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 222) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 223) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 224) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 933) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 934) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 17) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 18) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 19) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 20) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 441) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 442) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 443) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 444) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 445) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 446) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 447) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2133) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2134) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2135) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2136) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1900) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1901) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1902) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1903) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1904) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1905) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1906) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1907) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1908) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1909) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1066) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1067) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1068) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1069) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1070) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
//                    hút skh
                    if (item.itemTemplate.id != 1982) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1983) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1984) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1985) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1986) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1987) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1988) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1989) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1990) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1991) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1992) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1993) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1994) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1995) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1996) {
                        ItemMapService.gI().pickItem(player, item.itemMapId, true);
                    }
                }
            }

        } else {
            if (((Pet) player).master.charms.tdThuHut > System.currentTimeMillis()) {
                for (ItemMap item : items) {
//                    if (item.itemTemplate.type >= 0) {
//                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
//                    }
                    if (item.itemTemplate.id != 220) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 221) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 222) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 223) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 224) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 933) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 934) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 17) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 18) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 19) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 20) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 441) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 442) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 443) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 444) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 445) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 446) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 447) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2133) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2134) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2135) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 2136) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1900) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1901) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1902) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1903) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1904) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1905) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1906) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1907) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1908) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1909) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1066) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1067) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1068) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1069) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1070) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
//                    hút skh
                    if (item.itemTemplate.id != 1982) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1983) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1984) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1985) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1986) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1987) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1988) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1989) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1990) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1991) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1992) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1993) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1994) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1995) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                    if (item.itemTemplate.id != 1996) {
                        ItemMapService.gI().pickItem(((Pet) player).master, item.itemMapId, true);
                    }
                }
            }
        }
    }

    private List<ItemMap> mobReward(Player player, ItemMap itemTask, Message msg) {
//        nplayer
        List<ItemMap> itemReward = new ArrayList<>();
        try {
            int mapid = player.zone.map.mapId;

            //    if (mapid == 105 || mapid == 106 || mapid == 108 || mapid == 108 || mapid == 109 || mapid == 110) {
            //    if (Util.isTrue(1, 1000)) {
            //            ItemMap itemMap = Util.ratiDTL1(zone, Util.nextInt(555, 567), 1, player.location.x, player.location.y, player.id);
            //            Service.getInstance().dropItemMap(zone, itemMap);
            //        }
            //      }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 50)) {
                    ItemMap itemMap = Util.ratiDTL2(zone, Util.nextInt(441, 447), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
//            if (mapid == 3) {
//                if (Util.isTrue(100, 100)) {
//                    ItemMap itemMap = Util.ratiDTL21(zone, Util.nextInt(2000, 2001), 1, player.location.x, player.location.y, player.id);
//                    Service.getInstance().dropItemMap(zone, itemMap);
//                }
//            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 300)) {
                    ItemMap itemMap = Util.ratiDTL48(zone, Util.nextInt(17, 20), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 250)) {
                    ItemMap itemMap = Util.ratiDTL49(zone, Util.nextInt(220, 224), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 250)) {
                    ItemMap itemMap = Util.ratiDTL50(zone, Util.nextInt(220, 224), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 250)) {
                    ItemMap itemMap = Util.ratiDTL51(zone, Util.nextInt(220, 224), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 250)) {
                    ItemMap itemMap = Util.ratiDTL52(zone, Util.nextInt(220, 224), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
                if (Util.isTrue(1, 250)) {
                    ItemMap itemMap = Util.ratiDTL53(zone, Util.nextInt(220, 224), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            if (mapid == 590 || mapid == 132 || mapid == 133) {
                if (Util.isTrue(1, 150)) {
                    ItemMap itemMap = Util.ratiDTL47(zone, Util.nextInt(590, 590), 1, player.location.x, player.location.y, player.id);
                    Service.getInstance().dropItemMap(zone, itemMap);
                }
            }
            itemReward = this.getItemMobReward(player, this.location.x + Util.nextInt(-10, 10),
                    this.zone.map.yPhysicInTop(this.location.x, this.location.y));
            if (Util.isTrue(33, 100)) {
                if (Util.isTrue(3, 15)) {
                    if (tempId == 77) { // gấu tướng cướp 
                        ItemMap tv = new ItemMap(zone, 590, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                        itemReward.add(tv);
                    }
                }
            }
//            if (Util.isTrue(1, 30)) {               //barcoll Hirudegarn
//                if (Util.isTrue(1, 30)) {
//                    if (tempId == 70) { // hirudegan 
//                        ItemMap tv = new ItemMap(zone, 568, Util.nextInt(1, 1), player.location.x, player.location.y, player.id);
//                        itemReward.add(tv);
//                    }
//                }
//            }
//            if (Util.isTrue(1, 50)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap aotl = new ItemMap(zone, 555, Util.nextInt(1, 1), player.location.x, player.location.y, player.id);
//                    aotl.options.add(new Item.ItemOption(47, Util.nextInt(500, 700)));
//                    aotl.options.add(new Item.ItemOption(21, Util.nextInt(19, 19)));
//                    aotl.options.add(new Item.ItemOption(86, Util.nextInt(1, 1)));
//                    itemReward.add(aotl);
//                }
//            }
//            if (Util.isTrue(1, 50)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap aotl = new ItemMap(zone, 560, Util.nextInt(1, 1), player.location.x, player.location.y, player.id);
//                    aotl.options.add(new Item.ItemOption(47, Util.nextInt(500, 700)));
//                    aotl.options.add(new Item.ItemOption(21, Util.nextInt(19, 19)));
//                    aotl.options.add(new Item.ItemOption(86, Util.nextInt(1, 1)));
//                    itemReward.add(aotl);
//                }
//            }
//            if (Util.isTrue(1, 50)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap aotl = new ItemMap(zone, 557, Util.nextInt(1, 1), player.location.x, player.location.y, player.id);
//                    aotl.options.add(new Item.ItemOption(47, Util.nextInt(500, 700)));
//                    aotl.options.add(new Item.ItemOption(21, Util.nextInt(19, 19)));
//                    aotl.options.add(new Item.ItemOption(86, Util.nextInt(1, 1)));
//                    itemReward.add(aotl);
//                }
//            }
//
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 76, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 188, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 189, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            if (Util.isTrue(100, 100)) {
//                if (tempId == 70) { // hirudegan 
//                    ItemMap tv = new ItemMap(zone, 190, Util.nextInt(1, 10), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//
//            //  mảnh tl
//            if (mapid == 105 || mapid == 106 || mapid == 108 || mapid == 108 || mapid == 109 || mapid == 110) {
//                if (Util.isTrue(1, 40)) {
//                    ItemMap tv = new ItemMap(zone, Util.nextInt(1900, 1904), 1, player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // mảnh hd
//            if (mapid == 208 || mapid == 209 || mapid == 210) {
//                if (Util.isTrue(1, 50)) {
//                    ItemMap tv = new ItemMap(zone, Util.nextInt(1905, 1909), 1, player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // mảnh ts
//            if (mapid == 218) {
//                if (Util.isTrue(1, 80)) {
//                    ItemMap tv = new ItemMap(zone, Util.nextInt(1066, 1070), 1, player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//
//            //  mảnh btc2
//            if (mapid == 156) {
//                if (Util.isTrue(1, 20)) {
//                    ItemMap tv = new ItemMap(zone, 2133, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // mảnh btc3
//            if (mapid == 157) {
//                if (Util.isTrue(1, 20)) {
//                    ItemMap tv = new ItemMap(zone, 2134, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // mảnh btc4
//            if (mapid == 158) {
//                if (Util.isTrue(1, 20)) {
//                    ItemMap tv = new ItemMap(zone, 2135, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // mảnh btc5
//            if (mapid == 159) {
//                if (Util.isTrue(1, 20)) {
//                    ItemMap tv = new ItemMap(zone, 2136, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//
//            //  vỏ sò
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2137, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // vỏ ốc
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2138, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // da thú
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2139, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // lông thú
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2140, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // hạt giống
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2141, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//            // lông linh thú
//            if (mapid == 1 || mapid == 2 || mapid == 3 || mapid == 4 || mapid == 5 || mapid == 6 || mapid == 8 || mapid == 9 || mapid == 10 || mapid == 11 || mapid == 12 || mapid == 13 || mapid == 15 || mapid == 17 || mapid == 18 || mapid == 19 || mapid == 20 || mapid == 27 || mapid == 28 || mapid == 29 || mapid == 30 || mapid == 31 || mapid == 32 || mapid == 33 || mapid == 34 || mapid == 35 || mapid == 36 || mapid == 37 || mapid == 38 || mapid == 30) {
//                if (Util.isTrue(1, 4000)) {
//                    ItemMap tv = new ItemMap(zone, 2142, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//                }
//            }
//
//            if (Util.isTrue(15, 100)) {
//                if (Util.isTrue(1, 10)) {
//                    if (tempId == 0) { // mộc nhân 
//                        ItemMap tv = new ItemMap(zone, 933, 1, player.location.x, player.location.y, player.id);
//                        itemReward.add(tv);
//                    }
//                }
//                if (tempId == 0) { // mộc nhân 
//                    ItemMap tv = new ItemMap(zone, 934, 1, player.location.x, player.location.y, player.id);
//                    itemReward.add(tv);
//
//                    /// mảnh thiên thần linh, thiên sứ hủy diệt
//                }
//            }

            if (itemTask != null) {
                itemReward.add(itemTask);
            }
            msg.writer().writeByte(itemReward.size()); //sl item roi
            for (ItemMap itemMap : itemReward) {
                msg.writer().writeShort(itemMap.itemMapId);// itemmapid
                msg.writer().writeShort(itemMap.itemTemplate.id); // id item
                msg.writer().writeShort(itemMap.x); // xend item
                msg.writer().writeShort(itemMap.y); // yend item
                msg.writer().writeInt((int) itemMap.playerId); // id nhan nat
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return itemReward;
    }

    public List<ItemMap> getItemMobReward(Player player, int x, int yEnd) {
        List<ItemMap> list = new ArrayList<>();
        MobReward mobReward = Manager.MOB_REWARDS.get(this.tempId);
        if (mobReward == null) {
            return list;
        }
        List<ItemMobReward> items = mobReward.getItemReward();
        List<ItemMobReward> golds = mobReward.getGoldReward();
        if (!items.isEmpty()) {
            ItemMobReward item = items.get(Util.nextInt(0, items.size() - 1));
            ItemMap itemMap = item.getItemMap(zone, player, x, yEnd);
            if (itemMap != null) {
                list.add(itemMap);
            }
        }
        if (!golds.isEmpty()) {
            ItemMobReward gold = golds.get(Util.nextInt(0, golds.size() - 1));
            ItemMap itemMap = gold.getItemMap(zone, player, x, yEnd);
            if (itemMap != null) {
                list.add(itemMap);
            }
        }
        if (player.itemTime.isUseMayDo && Util.isTrue(100, 100) && this.tempId > 57 && this.tempId < 66) {
            list.add(new ItemMap(zone, 380, 1, x, player.location.y, player.id));
        }
        if (player.itemTime.isUseMayDo && Util.isTrue(1, 1) && this.tempId > 57 && this.tempId < 66) {
            list.add(new ItemMap(zone, 380, 1, x, player.location.y, player.id));
        }
        if (player.itemTime.isUseMayDo2 && Util.isTrue(1, 1) && this.tempId > 57 && this.tempId < 66) {
            list.add(new ItemMap(zone, 380, 1, x, player.location.y, player.id));

        }// vat phẩm rơi khi user maaáy dò adu hoa r o day ti code choa
        if (player.itemTime.isUseMayDo2 && Util.isTrue(1, 1) && this.tempId > 1) {
            list.add(new ItemMap(zone, 861, 0, x, player.location.y, player.id));// cai nay sua sau nha
        }

        // da thú
        if (this.zone.map.mapId == 1 || this.zone.map.mapId == 2 || this.zone.map.mapId == 3 || this.zone.map.mapId == 4 || this.zone.map.mapId == 5 || this.zone.map.mapId == 6 || this.zone.map.mapId == 8 || this.zone.map.mapId == 9 || this.zone.map.mapId == 10 || this.zone.map.mapId == 11 || this.zone.map.mapId == 12 || this.zone.map.mapId == 13 || this.zone.map.mapId == 15 || this.zone.map.mapId == 17 || this.zone.map.mapId == 18 || this.zone.map.mapId == 19 || this.zone.map.mapId == 20 || this.zone.map.mapId == 27 || this.zone.map.mapId == 28 || this.zone.map.mapId == 29 || this.zone.map.mapId == 30 || this.zone.map.mapId == 31 || this.zone.map.mapId == 32 || this.zone.map.mapId == 33 || this.zone.map.mapId == 34 || this.zone.map.mapId == 35 || this.zone.map.mapId == 36 || this.zone.map.mapId == 37 || this.zone.map.mapId == 38 || this.zone.map.mapId == 30) {
            if (Util.isTrue(1, 4000)) {
                ItemMap tv = new ItemMap(zone, 2139, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }

        if (this.zone.map.mapId == 1 || this.zone.map.mapId == 2
                || this.zone.map.mapId == 3 || this.zone.map.mapId == 4
                || this.zone.map.mapId == 5 || this.zone.map.mapId == 6
                || this.zone.map.mapId == 8 || this.zone.map.mapId == 9
                || this.zone.map.mapId == 10 || this.zone.map.mapId == 11
                || this.zone.map.mapId == 12 || this.zone.map.mapId == 13
                || this.zone.map.mapId == 15 || this.zone.map.mapId == 17
                || this.zone.map.mapId == 18 || this.zone.map.mapId == 19
                || this.zone.map.mapId == 20 || this.zone.map.mapId == 27
                || this.zone.map.mapId == 28 || this.zone.map.mapId == 29
                || this.zone.map.mapId == 30 || this.zone.map.mapId == 31
                || this.zone.map.mapId == 32 || this.zone.map.mapId == 33
                || this.zone.map.mapId == 34 || this.zone.map.mapId == 35
                || this.zone.map.mapId == 36 || this.zone.map.mapId == 37
                || this.zone.map.mapId == 38 || this.zone.map.mapId == 30
                || this.zone.map.mapId == 30 || (this.zone.map.mapId >= 92 && this.zone.map.mapId <= 94)
                || (this.zone.map.mapId >= 96 && this.zone.map.mapId <= 100)
                || (this.zone.map.mapId >= 68 && this.zone.map.mapId <= 72)
                || (this.zone.map.mapId >= 63 && this.zone.map.mapId <= 64)
                || (this.zone.map.mapId >= 66 && this.zone.map.mapId <= 67)
                 || (this.zone.map.mapId >= 73 && this.zone.map.mapId <= 77)
                 || (this.zone.map.mapId >= 81 && this.zone.map.mapId <= 83)
                 || (this.zone.map.mapId >= 79 && this.zone.map.mapId <= 80)
                || (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110)
                || (this.zone.map.mapId >= 208 && this.zone.map.mapId <= 211)
                || this.zone.map.mapId >= 155
                  || this.zone.map.mapId >= 218
                ) {
            if (Util.isTrue(30, 100)) {
                ItemMap tv = new ItemMap(zone, 1348, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
            
            if (Util.isTrue(20, 100)) {
                ItemMap tv = new ItemMap(zone,  Util.nextInt(886, 889), Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
            
            if (Util.isTrue(20, 100)) {
                ItemMap tv = new ItemMap(zone,  Util.nextInt(220, 224), Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        
        
        // lông thú
        if (this.zone.map.mapId == 1 || this.zone.map.mapId == 2 || this.zone.map.mapId == 3 || this.zone.map.mapId == 4 || this.zone.map.mapId == 5 || this.zone.map.mapId == 6 || this.zone.map.mapId == 8 || this.zone.map.mapId == 9 || this.zone.map.mapId == 10 || this.zone.map.mapId == 11 || this.zone.map.mapId == 12 || this.zone.map.mapId == 13 || this.zone.map.mapId == 15 || this.zone.map.mapId == 17 || this.zone.map.mapId == 18 || this.zone.map.mapId == 19 || this.zone.map.mapId == 20 || this.zone.map.mapId == 27 || this.zone.map.mapId == 28 || this.zone.map.mapId == 29 || this.zone.map.mapId == 30 || this.zone.map.mapId == 31 || this.zone.map.mapId == 32 || this.zone.map.mapId == 33 || this.zone.map.mapId == 34 || this.zone.map.mapId == 35 || this.zone.map.mapId == 36 || this.zone.map.mapId == 37 || this.zone.map.mapId == 38 || this.zone.map.mapId == 30) {
            if (Util.isTrue(1, 4000)) {
                ItemMap tv = new ItemMap(zone, 2140, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // hạt giống
        if (this.zone.map.mapId == 1 || this.zone.map.mapId == 2 || this.zone.map.mapId == 3 || this.zone.map.mapId == 4 || this.zone.map.mapId == 5 || this.zone.map.mapId == 6 || this.zone.map.mapId == 8 || this.zone.map.mapId == 9 || this.zone.map.mapId == 10 || this.zone.map.mapId == 11 || this.zone.map.mapId == 12 || this.zone.map.mapId == 13 || this.zone.map.mapId == 15 || this.zone.map.mapId == 17 || this.zone.map.mapId == 18 || this.zone.map.mapId == 19 || this.zone.map.mapId == 20 || this.zone.map.mapId == 27 || this.zone.map.mapId == 28 || this.zone.map.mapId == 29 || this.zone.map.mapId == 30 || this.zone.map.mapId == 31 || this.zone.map.mapId == 32 || this.zone.map.mapId == 33 || this.zone.map.mapId == 34 || this.zone.map.mapId == 35 || this.zone.map.mapId == 36 || this.zone.map.mapId == 37 || this.zone.map.mapId == 38 || this.zone.map.mapId == 30) {
            if (Util.isTrue(1, 4000)) {
                ItemMap tv = new ItemMap(zone, 2141, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // lông linh thú
        if (this.zone.map.mapId == 1 || this.zone.map.mapId == 2 || this.zone.map.mapId == 3 || this.zone.map.mapId == 4 || this.zone.map.mapId == 5 || this.zone.map.mapId == 6 || this.zone.map.mapId == 8 || this.zone.map.mapId == 9 || this.zone.map.mapId == 10 || this.zone.map.mapId == 11 || this.zone.map.mapId == 12 || this.zone.map.mapId == 13 || this.zone.map.mapId == 15 || this.zone.map.mapId == 17 || this.zone.map.mapId == 18 || this.zone.map.mapId == 19 || this.zone.map.mapId == 20 || this.zone.map.mapId == 27 || this.zone.map.mapId == 28 || this.zone.map.mapId == 29 || this.zone.map.mapId == 30 || this.zone.map.mapId == 31 || this.zone.map.mapId == 32 || this.zone.map.mapId == 33 || this.zone.map.mapId == 34 || this.zone.map.mapId == 35 || this.zone.map.mapId == 36 || this.zone.map.mapId == 37 || this.zone.map.mapId == 38 || this.zone.map.mapId == 30) {
            if (Util.isTrue(1, 4000)) {
                ItemMap tv = new ItemMap(zone, 2142, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }

        if (Util.isTrue(15, 100)) {
            if (Util.isTrue(1, 10)) {
                if (tempId == 0) { // mộc nhân 
                    ItemMap tv = new ItemMap(zone, 933, 1, player.location.x, player.location.y, player.id);
                    list.add(tv);
                }
            }
            if (tempId == 0) { // mộc nhân 
                ItemMap tv = new ItemMap(zone, 934, 1, player.location.x, player.location.y, player.id);
                list.add(tv);

                /// mảnh thiên thần linh, thiên sứ hủy diệt
            }
        }

//                mảnh tl
        if (this.zone.map.mapId == 105 || this.zone.map.mapId == 106 || this.zone.map.mapId == 108 || this.zone.map.mapId == 108 || this.zone.map.mapId == 109 || this.zone.map.mapId == 110) {
            if (Util.isTrue(1, 40)) {
                ItemMap tv = new ItemMap(zone, Util.nextInt(1900, 1904), 1, player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // mảnh hd
        if (this.zone.map.mapId == 208 || this.zone.map.mapId == 209 || this.zone.map.mapId == 210) {
            if (Util.isTrue(1, 50)) {
                ItemMap tv = new ItemMap(zone, Util.nextInt(1905, 1909), 1, player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // mảnh ts
        if (this.zone.map.mapId == 218) {
            if (Util.isTrue(1, 80)) {
                ItemMap tv = new ItemMap(zone, Util.nextInt(1066, 1070), 1, player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }

        //  mảnh btc2
        if (this.zone.map.mapId == 156) {
            if (Util.isTrue(1, 20)) {
                ItemMap tv = new ItemMap(zone, 2133, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // mảnh btc3
        if (this.zone.map.mapId == 157) {
            if (Util.isTrue(1, 20)) {
                ItemMap tv = new ItemMap(zone, 2134, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // mảnh btc4
        if (this.zone.map.mapId == 158) {
            if (Util.isTrue(1, 20)) {
                ItemMap tv = new ItemMap(zone, 2135, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }
        // mảnh btc5
        if (this.zone.map.mapId == 159) {
            if (Util.isTrue(1, 20)) {
                ItemMap tv = new ItemMap(zone, 2136, Util.nextInt(1, 3), player.location.x, player.location.y, player.id);
                list.add(tv);
            }
        }

        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Quanthanlinh = ItemService.gI().createNewItem((short) (556));
                Quanthanlinh.itemOptions.add(new Item.ItemOption(22, Util.nextInt(55, 65)));
                Quanthanlinh.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Quanthanlinh);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Quanthanlinh.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Quanthanlinhxd = ItemService.gI().createNewItem((short) (560));
                Quanthanlinhxd.itemOptions.add(new Item.ItemOption(22, Util.nextInt(45, 55)));
                Quanthanlinhxd.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Quanthanlinhxd);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Quanthanlinhxd.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Quanthanlinhnm = ItemService.gI().createNewItem((short) (558));
                Quanthanlinhnm.itemOptions.add(new Item.ItemOption(22, Util.nextInt(45, 60)));
                Quanthanlinhnm.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Quanthanlinhnm);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Quanthanlinhnm.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Aothanlinh = ItemService.gI().createNewItem((short) (555));
                Aothanlinh.itemOptions.add(new Item.ItemOption(47, Util.nextInt(500, 600)));
                Aothanlinh.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Aothanlinh);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Aothanlinh.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Aothanlinhnm = ItemService.gI().createNewItem((short) (557));
                Aothanlinhnm.itemOptions.add(new Item.ItemOption(47, Util.nextInt(400, 550)));
                Aothanlinhnm.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Aothanlinhnm);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Aothanlinhnm.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Aothanlinhxd = ItemService.gI().createNewItem((short) (559));
                Aothanlinhxd.itemOptions.add(new Item.ItemOption(47, Util.nextInt(600, 700)));
                Aothanlinhxd.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Aothanlinhxd);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Aothanlinhxd.template.name);
            }
        }

        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Gangthanlinh = ItemService.gI().createNewItem((short) (562));
                Gangthanlinh.itemOptions.add(new Item.ItemOption(0, Util.nextInt(6000, 7000)));
                Gangthanlinh.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Gangthanlinh);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Gangthanlinh.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Gangthanlinhxd = ItemService.gI().createNewItem((short) (566));
                Gangthanlinhxd.itemOptions.add(new Item.ItemOption(0, Util.nextInt(6500, 7500)));
                Gangthanlinhxd.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Gangthanlinhxd);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Gangthanlinhxd.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Gangthanlinhnm = ItemService.gI().createNewItem((short) (564));
                Gangthanlinhnm.itemOptions.add(new Item.ItemOption(0, Util.nextInt(5500, 6500)));
                Gangthanlinhnm.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Gangthanlinhnm);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Gangthanlinhnm.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Giaythanlinh = ItemService.gI().createNewItem((short) (563));
                Giaythanlinh.itemOptions.add(new Item.ItemOption(23, Util.nextInt(50, 60)));
                Giaythanlinh.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Giaythanlinh);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Giaythanlinh.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Giaythanlinhxd = ItemService.gI().createNewItem((short) (567));
                Giaythanlinhxd.itemOptions.add(new Item.ItemOption(23, Util.nextInt(55, 65)));
                Giaythanlinhxd.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Giaythanlinhxd);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Giaythanlinhxd.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Giaythanlinhnm = ItemService.gI().createNewItem((short) (565));
                Giaythanlinhnm.itemOptions.add(new Item.ItemOption(23, Util.nextInt(65, 75)));
                Giaythanlinhnm.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Giaythanlinhnm);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Giaythanlinhnm.template.name);
            }
        }
        if (this.zone.map.mapId >= 105 && this.zone.map.mapId <= 110) {
            if (Util.isTrue(1, 1000)) {
                Item Nhanthanlinh = ItemService.gI().createNewItem((short) (561));
                Nhanthanlinh.itemOptions.add(new Item.ItemOption(14, Util.nextInt(13, 16)));
                Nhanthanlinh.itemOptions.add(new Item.ItemOption(21, Util.nextInt(15, 17)));
                InventoryServiceNew.gI().addItemBag(player, Nhanthanlinh);
                InventoryServiceNew.gI().sendItemBags(player);
                Service.gI().sendThongBao(player, "Bạn vừa nhận được " + Nhanthanlinh.template.name);
            }
        }

//        if (player.getSession().actived && Util.isTrue(100, 100)) {
//            list.add(new ItemMap(zone, 3, 1, x, player.location.y, player.id));
//        }
//        if (Util.nextInt(0,1) < 1){
//            list.add(new ItemMap(zone, 1246, 1, x, player.location.y, player.id));
//        }
//          if (Util.nextInt(0,1) < 1){
//            list.add(new ItemMap(zone, 1247, 1, x, player.location.y, player.id));
//        }
//            if (Util.nextInt(0,1) < 1){
//            list.add(new ItemMap(zone, 1248, 1, x, player.location.y, player.id));
//        }
        if (this.zone.map.mapId == 212) {
            if (Util.isTrue(2, 50)) {
                ItemMap it = new ItemMap(zone, 568, 1, player.location.x, player.location.y, player.id);
                list.add(it);
                Service.gI().sendThongBao(player, "Bạn đã nhận được quả trứng");
            } else if (Util.isTrue(10, 100)) {
                int totalgold = Util.nextInt(1, 10);
                ItemMap it = new ItemMap(zone, 457, totalgold, player.location.x, player.location.y, player.id);
                list.add(it);
                Service.gI().sendThongBao(player, "Bạn đã nhận được " + totalgold + " Thỏi vàng");
            }
//            if (Util.isTrue(40, 100)) {
//                ItemMap it = new ItemMap(this.zone, 568, 1, x, player.location.y, player.id);
//                list.add(it);
//            }
//        }
//        if (this.tempId == 0 && (this.zone.map.mapId == 0 || this.zone.map.mapId == 7 || this.zone.map.mapId == 14) && player.cFlag != 0) {
//            ItemMap it = new ItemMap(this.zone, 590, 1, x, player.location.y, player.id);
//            list.add(it);
        }

        if (this.zone.map.mapId == 232) {
            if (Util.isTrue(2, 100)) {
                ItemMap it = new ItemMap(zone, 2000, 1, player.location.x, player.location.y, player.id);
                list.add(it);
            }
            if (Util.isTrue(2, 100)) {
                ItemMap it = new ItemMap(zone, 2001, 1, player.location.x, player.location.y, player.id);
                list.add(it);
            }

            if (Util.isTrue(2, 100)) {
                ItemMap it = new ItemMap(zone, 2002, 1, player.location.x, player.location.y, player.id);
                list.add(it);
            }
        }
        if (player.setClothes.godClothes && MapService.gI().isMapCold(this.zone.map) && Util.isTrue(3, 100)) {
            list.add(new ItemMap(this.zone, Util.nextInt(663, 667), 1, player.location.x, player.location.y, player.id));
        }
        return list;
    }

    private ItemMap dropItemTask(Player player) {
        ItemMap itemMap = null;
        switch (this.tempId) {
            case ConstMob.KHUNG_LONG:
            case ConstMob.LON_LOI:
            case ConstMob.QUY_DAT:
                if (TaskService.gI().getIdTask(player) == ConstTask.TASK_2_0) {
                    itemMap = new ItemMap(this.zone, 73, 1, this.location.x, this.location.y, player.id);
                }
                break;
        }
        switch (this.tempId) {
            case ConstMob.THAN_LAN_ME:
                if (TaskService.gI().getIdTask(player) == ConstTask.TASK_9_1) {
                    itemMap = new ItemMap(this.zone, 20, 1, this.location.x, this.location.y, player.id);
                }
                break;
            case ConstMob.HEO_XAYDA_ME:
            case ConstMob.OC_MUON_HON:
            case ConstMob.OC_SEN:
                if (TaskService.gI().getIdTask(player) == ConstTask.TASK_15_1) {
                    itemMap = new ItemMap(this.zone, 85, 1, this.location.x, this.location.y, player.id);
                }
        }
        if (itemMap != null) {
            return itemMap;
        }
        return null;
    }

    private void sendMobStillAliveAffterAttacked(int dameHit, boolean crit) {
        Message msg;
        try {
            msg = new Message(-9);
            msg.writer().writeByte(this.id);
            msg.writer().writeInt(Util.TamkjllGH(this.point.gethp()));
            msg.writer().writeInt(dameHit);
            msg.writer().writeBoolean(crit); // chí mạng
            msg.writer().writeInt(-1);
            Service.getInstance().sendMessAllPlayerInMap(this.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    private void BigbossAttack() {
        if (!isDie() && !this.effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastTimeAttackPlayer, 1000)) {
            Message msg = null;
            try {
                switch (this.tempId) {
                    case 70: // Hirudegarn 
                        if (!Util.canDoWithTime(lastTimeAttackPlayer, 3000)) {
                            return;
                        }
                        // 0: bắn - 1: Quật đuôi - 2: dậm chân - 3: Bay - 4: tấn công - 5: Biến hình - 6: Biến hình lên cấp
                        // 7: vận chiêu - 8: Di chuyển - 9: Die
                        int[] idAction = new int[]{1, 2, 7};
                        if (this.level >= 2) {
                            idAction = new int[]{1, 2};
                        }
                        action = action == 7 ? 0 : idAction[Util.nextInt(0, idAction.length - 1)];
                        int index = Util.nextInt(0, zone.getPlayers().size() - 1);
                        Player player = zone.getPlayers().get(index);
                        if (player == null || player.isDie()) {
                            return;
                        }
                        if (action == 1) {
                            cx = (short) player.location.x;
                            Service.gI().sendBigBoss2(zone, 8, this);
                        }
                        msg = new Message(101);
                        msg.writer().writeByte(action);
                        if (action >= 0 && action <= 4) {
                            if (action == 1) {
                                msg.writer().writeByte(1);
                                int dame = (int) player.injured(player, (int) this.point.getDameAttack(), false, true);
                                if (dame <= 0) {
                                    dame = 1;
                                }
                                msg.writer().writeInt((int) player.id);
                                msg.writer().writeInt(dame);
                            } else if (action == 3) {
                                cx = (short) player.location.x;
                                msg.writer().writeShort(cx);
                                msg.writer().writeShort(this.location.y);
                            } else {
                                msg.writer().writeByte(zone.getNotBosses().size());
                                for (int i = 0; i < zone.getNotBosses().size(); i++) {
                                    Player pl = zone.getNotBosses().get(i);
                                    int dame = (int) player.injured(player, (int) this.point.getDameAttack(), false, true);
                                    if (dame <= 0) {
                                        dame = 1;
                                    }
                                    msg.writer().writeInt((int) pl.id);
                                    msg.writer().writeInt(dame);
                                }
                            }
                        } else {
                            if (action == 6 || action == 8) {
                                cx = (short) player.location.x;
                                msg.writer().writeShort(cx);
                                msg.writer().writeShort(this.location.y);
                            }
                        }
                        Service.gI().sendMessAllPlayerInMap(zone, msg);
                        lastTimeAttackPlayer = System.currentTimeMillis();
                        break;
                }
            } catch (Exception e) {
//                Util.debug("ERROR BIG BOSS");
            } finally {
                if (msg != null) {
                    msg.cleanup();
                    msg = null;
                }
            }
        }
    }
}
