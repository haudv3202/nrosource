package com.girlkun.models.boss.daihoivothuat;

import com.girlkun.models.boss.BossID;
import com.girlkun.models.boss.BossesData;
import com.girlkun.models.boss.daihoivothuat.BossDHVT;
import com.girlkun.models.player.Player;


/**
 *
 * @author BTH fix
 */
public class Yamcha extends BossDHVT {

    public Yamcha(Player player) throws Exception {
        super(BossID.YAMCHA, BossesData.YAMCHA);
        this.playerAtt = player;
    }
}
