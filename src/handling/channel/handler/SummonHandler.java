/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package handling.channel.handler;

import client.*;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.GameConstants;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import server.MapleItemInformationProvider;
import server.MapleStatEffect;
import server.Randomizer;
import server.life.MapleMonster;
import server.maps.*;
import server.movement.LifeMovementFragment;
import tools.AttackPair;
import tools.Pair;
import tools.data.LittleEndianAccessor;
import tools.packet.CField;
import tools.packet.CField.EffectPacket;
import tools.packet.CField.SummonPacket;
import tools.packet.MobPacket;

public class SummonHandler {

    public static void MoveDragon(final LittleEndianAccessor slea, final MapleCharacter chr) {
        slea.skip(8); //POS
        final List<LifeMovementFragment> res = MovementParse.parseMovement(slea, 5);
        if (chr != null && chr.getDragon() != null && res.size() > 0) {
            final Point pos = chr.getDragon().getPosition();
            MovementParse.updatePosition(res, chr.getDragon(), 0);
            if (!chr.isHidden()) {
                chr.getMap().broadcastMessage(chr, CField.moveDragon(chr.getDragon(), pos, res), chr.getTruePosition());
            }
            
        }
    }

    public static void MoveSummon(final LittleEndianAccessor slea, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final MapleMapObject obj = chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null) {
            return;
        }
        if (obj instanceof MapleDragon) {
            MoveDragon(slea, chr);
            return;
        }
        final MapleSummon sum = (MapleSummon) obj;
        if (sum.getOwnerId() != chr.getId() || sum.getSkillLevel() <= 0 || sum.getMovementType() == SummonMovementType.STATIONARY) {
            return;
        }
        slea.skip(8); //startPOS
        final List<LifeMovementFragment> res = MovementParse.parseMovement(slea, 4);

        final Point pos = sum.getPosition();
        MovementParse.updatePosition(res, sum, 0);
        if (res.size() > 0) {
            chr.getMap().broadcastMessage(chr, SummonPacket.moveSummon(chr.getId(), sum.getObjectId(), pos, res), sum.getTruePosition());
        }
    }

    public static void DamageSummon(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final int unkByte = slea.readByte();
        final int damage = slea.readInt();
        final int monsterIdFrom = slea.readInt();
        //       slea.readByte(); // stance

        final Iterator<MapleSummon> iter = chr.getSummonsReadLock().iterator();
        MapleSummon summon;
        boolean remove = false;
        try {
            while (iter.hasNext()) {
                summon = iter.next();
                if (summon.isPuppet() && summon.getOwnerId() == chr.getId() && damage > 0) { //We can only have one puppet(AFAIK O.O) so this check is safe.
                    summon.addHP((short) -damage);
                    if (summon.getHP() <= 0) {
                        remove = true;
                    }
                    chr.getMap().broadcastMessage(chr, SummonPacket.damageSummon(chr.getId(), summon.getSkill(), damage, unkByte, monsterIdFrom), summon.getTruePosition());
                    break;
                }
            }
        } finally {
            chr.unlockSummonsReadLock();
        }
        if (remove) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
        }
    }

    /*     */   public static void SummonAttack(LittleEndianAccessor slea, MapleClient c, MapleCharacter chr)
/*     */   {
/* 158 */     if ((chr == null) || (!chr.isAlive()) || (chr.getMap() == null)) {
/* 159 */       return;
/*     */     }
/* 161 */     MapleMap map = chr.getMap();
/* 162 */     MapleMapObject obj = map.getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
/* 163 */     if ((obj == null) || (!(obj instanceof MapleSummon))) {
/* 164 */       chr.dropMessage(5, "The summon has disappeared.");
/* 165 */       return;
/*     */     }
/* 167 */     MapleSummon summon = (MapleSummon)obj;
/* 168 */     if ((summon.getOwnerId() != chr.getId()) || (summon.getSkillLevel() <= 0)) {
/* 169 */       chr.dropMessage(5, "Error.");
/* 170 */       return;
/*     */     }
/* 172 */     SummonSkillEntry sse = SkillFactory.getSummonData(summon.getSkill());
/* 173 */     if ((summon.getSkill() / 1000000 != 35) && (summon.getSkill() != 33101008) && (sse == null)) {
/* 174 */       chr.dropMessage(5, "Error in processing attack.");
/* 175 */       return;
/*     */     }
/* 177 */     if (!GameConstants.GMS) {
/* 178 */       slea.skip(8);
/*     */     }
/* 180 */     slea.readInt();
/* 186 */     if (!GameConstants.GMS) {
/* 187 */       slea.skip(8);
/*     */     }
/* 189 */     byte animation = slea.readByte();
/* 190 */     if (!GameConstants.GMS) {
/* 191 */       slea.skip(8);
/*     */     }
/* 193 */     byte numAttacked = slea.readByte();
/* 194 */    // if ((sse != null) && (numAttacked > sse.mobCount)) {
/* 195 */      // chr.dropMessage(5, "Warning: Attacking more monster than summon can do");
/* 198 */     //  return;
/*     */     //}
/* 200 */     slea.skip(summon.getSkill() == 35111002 ? 24 : 12);
/* 201 */     List<Pair<Integer, Integer>> allDamage = new ArrayList<>();
/* 202 */     for (int i = 0; i < numAttacked; i++) {
/* 203 */       MapleMonster mob = map.getMonsterByOid(slea.readInt());
/*     */ 
/* 205 */       if (mob == null) {
/*     */         continue;
/*     */       }
/* 208 */       slea.skip(18);
/* 209 */       int damge = slea.readInt();
/* 210 */       allDamage.add(new Pair(Integer.valueOf(mob.getObjectId()), Integer.valueOf(damge)));
/*     */     }
/*     */ 
/* 213 */     map.broadcastMessage(chr, CField.SummonPacket.summonAttack(summon.getOwnerId(), summon.getObjectId(), animation, allDamage, chr.getLevel(), false), summon.getTruePosition());
/*     */ 
/* 215 */     Skill summonSkill = SkillFactory.getSkill(summon.getSkill());
/* 216 */     MapleStatEffect summonEffect = summonSkill.getEffect(summon.getSkillLevel());
/* 217 */     if (summonEffect == null) {
/* 218 */       chr.dropMessage(5, "Error in attack.");
/* 219 */       return;
/*     */     }
/* 221 */    for (Pair<Integer, Integer> attackEntry : allDamage) {
/* 222 */       int toDamage = ((Integer)attackEntry.right).intValue();
/* 223 */       MapleMonster mob = map.getMonsterByOid(((Integer)attackEntry.left).intValue());
/* 224 */       if (mob == null) {
/*     */         continue;
/*     */       }
/* 230 */       if ((toDamage > 0) && (summonEffect.getMonsterStati().size() > 0) && 
/* 231 */         (summonEffect.makeChanceResult())) {
/* 232 */         for (Map.Entry z : summonEffect.getMonsterStati().entrySet()) {
/* 233 */           mob.applyStatus(chr, new MonsterStatusEffect((MonsterStatus)z.getKey(), (Integer)z.getValue(), summonSkill.getId(), null, false), summonEffect.isPoison(), 4000, true, summonEffect);
/*     */         }
/*     */       }
/*     */ 
/* 237 */       if ((toDamage < chr.getStat().getCurrentMaxBaseDamage() * 5.0 * (summonEffect.getSelfDestruction() + summonEffect.getDamage() + chr.getStat().getDamageIncrease(summonEffect.getSourceId())) / 100.0)) {
/* 238 */         mob.damage(chr, toDamage, true);
/* 239 */         chr.checkMonsterAggro(mob);
/* 240 */         if (!mob.isAlive())
/* 241 */           chr.getClient().getSession().write(MobPacket.killMonster(mob.getObjectId(), 1));
/*     */       }
/*     */     }
/* 250 */     if (!summon.isMultiAttack()) {
/* 251 */       chr.getMap().broadcastMessage(CField.SummonPacket.removeSummon(summon, true));
/* 252 */       chr.getMap().removeMapObject(summon);
/* 253 */       chr.removeVisibleMapObject(summon);
/* 254 */       chr.removeSummon(summon);
/* 255 */       if (summon.getSkill() != 35121011)
/* 256 */         chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
/*     */     }
/*     */   }
    
 /*   public static void SummonAttack(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || !chr.isAlive() || chr.getMap() == null) {
            return;
        }
        final MapleMap map = chr.getMap();
        final MapleMapObject obj = map.getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null || !(obj instanceof MapleSummon)) {
            chr.dropMessage(5, "The summon has disappeared.");
            return;
        }
        final MapleSummon summon = (MapleSummon) obj;
        if (summon.getOwnerId() != chr.getId() || summon.getSkillLevel() <= 0) {
            chr.dropMessage(5, "Error.");
            return;
        }
        final SummonSkillEntry sse = SkillFactory.getSummonData(summon.getSkill());
        if (summon.getSkill() / 1000000 != 35 && summon.getSkill() != 33101008 && sse == null) {
            chr.dropMessage(5, "Error in processing attack.");
            return;
        }
        if (!GameConstants.GMS) {
            slea.skip(8);
        }
        int tick = slea.readInt();
        if (!GameConstants.GMS) {
            slea.skip(8);
        }
        final byte animation = slea.readByte();
        if (!GameConstants.GMS) {
            slea.skip(8);
        }
        final byte numAttacked = slea.readByte();
        if (sse != null && numAttacked > sse.mobCount) {
            chr.dropMessage(5, "Warning: Attacking more monster than summon can do");
            //AutobanManager.getInstance().autoban(c, "Attacking more monster that summon can do (Skillid : "+summon.getSkill()+" Count : " + numAttacked + ", allowed : " + sse.mobCount + ")");
            return;
        }
        slea.skip(summon.getSkill() == 35111002 ? 24 : 12); //some pos stuff
        final List<Pair<Integer, Integer>> allDamage = new ArrayList<>();
        for (int i = 0; i < numAttacked; i++) {
            final MapleMonster mob = map.getMonsterByOid(slea.readInt());

            if (mob == null) {
                continue;
            }
            slea.skip(18); // who knows
            final int damge = slea.readInt();
            allDamage.add(new Pair<>(mob.getObjectId(), damge));
        }
        //if (!summon.isChangedMap()) {
        map.broadcastMessage(chr, SummonPacket.summonAttack(summon.getOwnerId(), summon.getObjectId(), animation, allDamage, chr.getLevel(), false), summon.getTruePosition());
        //}
        final Skill summonSkill = SkillFactory.getSkill(summon.getSkill());
        final MapleStatEffect summonEffect = summonSkill.getEffect(summon.getSkillLevel());
        if (summonEffect == null) {
            chr.dropMessage(5, "Error in attack.");
            return;
        }
        for (Pair<Integer, Integer> attackEntry : allDamage) {
            final int toDamage = attackEntry.right;
            final MapleMonster mob = map.getMonsterByOid(attackEntry.left);
            if (mob == null) {
                continue;
            }
            if (toDamage > 0 && summonEffect.getMonsterStati().size() > 0) {
                if (summonEffect.makeChanceResult()) {
                    for (Map.Entry<MonsterStatus, Integer> z : summonEffect.getMonsterStati().entrySet()) {
                        mob.applyStatus(chr, new MonsterStatusEffect(z.getKey(), z.getValue(), summonSkill.getId(), null, false), summonEffect.isPoison(), 4000, true, summonEffect);
                    }
                }
            }
            if (toDamage < (chr.getStat().getCurrentMaxBaseDamage() * 5.0 * (summonEffect.getSelfDestruction() + summonEffect.getDamage() + chr.getStat().getDamageIncrease(summonEffect.getSourceId())) / 100.0)) { //10 x dmg.. eh
                mob.damage(chr, toDamage, true);
                chr.checkMonsterAggro(mob);
                if (!mob.isAlive()) {
                    chr.getClient().getSession().write(MobPacket.killMonster(mob.getObjectId(), 1));
                }
            } else {
                break;
            }
        }
        if (!summon.isMultiAttack()) {
            chr.getMap().broadcastMessage(SummonPacket.removeSummon(summon, true));
            chr.getMap().removeMapObject(summon);
            chr.removeVisibleMapObject(summon);
            chr.removeSummon(summon);
            if (summon.getSkill() != 35121011) {
                chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
            }
        }
    }

*/ 
    public static void RemoveSummon(final LittleEndianAccessor slea, final MapleClient c) {
        final MapleMapObject obj = c.getPlayer().getMap().getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null || !(obj instanceof MapleSummon)) {
            return;
        }
        final MapleSummon summon = (MapleSummon) obj;
        if (summon.getOwnerId() != c.getPlayer().getId() || summon.getSkillLevel() <= 0) {
            c.getPlayer().dropMessage(5, "Error.");
            return;
        }
        if (summon.getSkill() == 35111002 || summon.getSkill() == 35121010) { //rock n shock, amp
            return;
        }
        c.getPlayer().getMap().broadcastMessage(SummonPacket.removeSummon(summon, true));
        c.getPlayer().getMap().removeMapObject(summon);
        c.getPlayer().removeVisibleMapObject(summon);
        c.getPlayer().removeSummon(summon);
        if (summon.getSkill() != 35121011) {
            c.getPlayer().cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
            //TODO: Multi Summoning, must do something about hack buffstat
        }
    }

    public static void SubSummon(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMapObject obj = chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null || !(obj instanceof MapleSummon)) {
            return;
        }
        final MapleSummon sum = (MapleSummon) obj;
        if (sum == null || sum.getOwnerId() != chr.getId() || sum.getSkillLevel() <= 0 || !chr.isAlive()) {
            return;
        }
        switch (sum.getSkill()) {
            case 35121009:
                if (!chr.canSummon(2000)) {
                    return;
                }
				final int skillId = slea.readInt(); // 35121009?
				if (sum.getSkill() != skillId) {
					return;
				}
				slea.skip(1); // 0E?
			slea.readInt();
                for (int i = 0; i < 3; i++) {
                    final MapleSummon tosummon = new MapleSummon(chr, SkillFactory.getSkill(35121011).getEffect(sum.getSkillLevel()), new Point(sum.getTruePosition().x, sum.getTruePosition().y - 5), SummonMovementType.WALK_STATIONARY);
                    chr.getMap().spawnSummon(tosummon);
                    chr.addSummon(tosummon);
                }
                break;
            case 35111011: //healing
                if (!chr.canSummon(1000)) {
                    return;
                }
                chr.addHP((int) (chr.getStat().getCurrentMaxHp() * SkillFactory.getSkill(sum.getSkill()).getEffect(sum.getSkillLevel()).getHp() / 100.0));
                chr.getClient().getSession().write(EffectPacket.showOwnBuffEffect(sum.getSkill(), 2, chr.getLevel(), sum.getSkillLevel()));
                chr.getMap().broadcastMessage(chr, EffectPacket.showBuffeffect(chr.getId(), sum.getSkill(), 2, chr.getLevel(), sum.getSkillLevel()), false);
                break;
            case 1321007: //beholder
                Skill bHealing = SkillFactory.getSkill(slea.readInt());
                final int bHealingLvl = chr.getTotalSkillLevel(bHealing);
                if (bHealingLvl <= 0 || bHealing == null) {
                    return;
                }
                final MapleStatEffect healEffect = bHealing.getEffect(bHealingLvl);
                if (bHealing.getId() == 1320009) {
                    healEffect.applyTo(chr);
                } else if (bHealing.getId() == 1320008) {
                    if (!chr.canSummon(healEffect.getX() * 1000)) {
                        return;
                    }
                    chr.addHP(healEffect.getHp());
                }
                chr.getClient().getSession().write(EffectPacket.showOwnBuffEffect(sum.getSkill(), 2, chr.getLevel(), bHealingLvl));
                chr.getMap().broadcastMessage(SummonPacket.summonSkill(chr.getId(), sum.getSkill(), bHealing.getId() == 1320008 ? 5 : (Randomizer.nextInt(3) + 6)));
                chr.getMap().broadcastMessage(chr, EffectPacket.showBuffeffect(chr.getId(), sum.getSkill(), 2, chr.getLevel(), bHealingLvl), false);
                break;
        }
        if (GameConstants.isAngel(sum.getSkill())) {
            if (sum.getSkill() % 10000 == 1087) {
                MapleItemInformationProvider.getInstance().getItemEffect(2022747).applyTo(chr);
            } else if (sum.getSkill() % 10000 == 1179) {
                MapleItemInformationProvider.getInstance().getItemEffect(2022823).applyTo(chr);
            } else {
                MapleItemInformationProvider.getInstance().getItemEffect(2022746).applyTo(chr);
            }
            chr.getClient().getSession().write(EffectPacket.showOwnBuffEffect(sum.getSkill(), 2, 2, 1));
            chr.getMap().broadcastMessage(chr, EffectPacket.showBuffeffect(chr.getId(), sum.getSkill(), 2, 2, 1), false);
        }
    }

    public static void SummonPVP(final LittleEndianAccessor slea, final MapleClient c) {
        final MapleCharacter chr = c.getPlayer();
        if (chr == null || chr.isHidden() || !chr.isAlive() || chr.hasBlockedInventory() || chr.getMap() == null || !chr.inPVP() || !chr.getEventInstance().getProperty("started").equals("1")) {
            return;
        }
        final MapleMap map = chr.getMap();
        final MapleMapObject obj = map.getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null || !(obj instanceof MapleSummon)) {
            chr.dropMessage(5, "The summon has disappeared.");
            return;
        }
        int tick = -1;
        if (slea.available() == 27) {
            slea.skip(23);
            tick = slea.readInt();
        }
        final MapleSummon summon = (MapleSummon) obj;
        if (summon.getOwnerId() != chr.getId() || summon.getSkillLevel() <= 0) {
            chr.dropMessage(5, "Error.");
            return;
        }
        final Skill skil = SkillFactory.getSkill(summon.getSkill());
        final MapleStatEffect effect = skil.getEffect(summon.getSkillLevel());
        final int lvl = Integer.parseInt(chr.getEventInstance().getProperty("lvl"));
        final int type = Integer.parseInt(chr.getEventInstance().getProperty("type"));
        final int ourScore = Integer.parseInt(chr.getEventInstance().getProperty(String.valueOf(chr.getId())));
        int addedScore = 0;
        final boolean magic = skil.isMagic();
        boolean killed = false, didAttack = false;
        double maxdamage = lvl == 3 ? chr.getStat().getCurrentMaxBasePVPDamageL() : chr.getStat().getCurrentMaxBasePVPDamage();
        maxdamage *= (effect.getDamage() + chr.getStat().getDamageIncrease(summon.getSkill())) / 100.0;
        int mobCount = 1, attackCount = 1, ignoreDEF = chr.getStat().ignoreTargetDEF;

        final SummonSkillEntry sse = SkillFactory.getSummonData(summon.getSkill());
        if (summon.getSkill() / 1000000 != 35 && summon.getSkill() != 33101008 && sse == null) {
            chr.dropMessage(5, "Error in processing attack.");
            return;
        }
        Point lt, rb;
        if (sse != null) {
            if (sse.delay > 0) {
                if (tick != -1) {
                    summon.CheckSummonAttackFrequency(chr, tick);
                } else {
                    summon.CheckPVPSummonAttackFrequency(chr);
                }
            }
            mobCount = sse.mobCount;
            attackCount = sse.attackCount;
            lt = sse.lt;
            rb = sse.rb;
        } else {
            lt = new Point(-100, -100);
            rb = new Point(100, 100);
        }
        final Rectangle box = MapleStatEffect.calculateBoundingBox(chr.getTruePosition(), chr.isFacingLeft(), lt, rb, 0);
        List<AttackPair> ourAttacks = new ArrayList<>();
        List<Pair<Integer, Boolean>> attacks;
        maxdamage *= chr.getStat().dam_r / 100.0;
        for (MapleMapObject mo : chr.getMap().getCharactersIntersect(box)) {
            final MapleCharacter attacked = (MapleCharacter) mo;
            if (attacked.getId() != chr.getId() && attacked.isAlive() && !attacked.isHidden() && (type == 0 || attacked.getTeam() != chr.getTeam())) {
                double rawDamage = maxdamage / Math.max(0, ((magic ? attacked.getStat().mdef : attacked.getStat().wdef) * Math.max(1.0, 100.0 - ignoreDEF) / 100.0) * (type == 3 ? 0.1 : 0.25));
                if (attacked.getBuffedValue(MapleBuffStat.INVINCIBILITY) != null || PlayersHandler.inArea(attacked)) {
                    rawDamage = 0;
                }
                rawDamage += (rawDamage * chr.getDamageIncrease(attacked.getId()) / 100.0);
                rawDamage *= attacked.getStat().mesoGuard / 100.0;
                rawDamage = attacked.modifyDamageTaken(rawDamage, attacked).left;
                final double min = (rawDamage * chr.getStat().trueMastery / 100);
                attacks = new ArrayList<>(attackCount);
                int totalMPLoss = 0, totalHPLoss = 0;
                for (int i = 0; i < attackCount; i++) {
                    int mploss = 0;
                    double ourDamage = Randomizer.nextInt((int) Math.abs(Math.round(rawDamage - min)) + 1) + min;
                    if (attacked.getStat().dodgeChance > 0 && Randomizer.nextInt(100) < attacked.getStat().dodgeChance) {
                        ourDamage = 0;
                        //i dont think level actually matters or it'd be too op
                        //} else if (attacked.getLevel() > chr.getLevel() && Randomizer.nextInt(100) < (attacked.getLevel() - chr.getLevel())) {
                        //	ourDamage = 0;
                    }
                    if (attacked.getBuffedValue(MapleBuffStat.MAGIC_GUARD) != null) {
                        mploss = (int) Math.min(attacked.getStat().getMp(), (ourDamage * attacked.getBuffedValue(MapleBuffStat.MAGIC_GUARD).doubleValue() / 100.0));
                    }
                    ourDamage -= mploss;
                    if (attacked.getBuffedValue(MapleBuffStat.INFINITY) != null) {
                        mploss = 0;
                    }
                    attacks.add(new Pair<>((int) Math.floor(ourDamage), false));

                    totalHPLoss += Math.floor(ourDamage);
                    totalMPLoss += mploss;
                }
                attacked.addMPHP(-totalHPLoss, -totalMPLoss);
                ourAttacks.add(new AttackPair(attacked.getId(), attacked.getPosition(), attacks));
                if (totalHPLoss > 0) {
                    didAttack = true;
                }
                if (attacked.getStat().getHPPercent() <= 20) {
                    SkillFactory.getSkill(PlayerStats.getSkillByJob(93, attacked.getJob())).getEffect(1).applyTo(attacked);
                }
                if (effect != null) {
                    if (effect.getMonsterStati().size() > 0 && effect.makeChanceResult()) {
                        for (Map.Entry<MonsterStatus, Integer> z : effect.getMonsterStati().entrySet()) {
                            MapleDisease d = MonsterStatus.getLinkedDisease(z.getKey());
                            if (d != null) {
                                attacked.giveDebuff(d, z.getValue(), effect.getDuration(), d.getDisease(), 1);
                            }
                        }
                    }
                    effect.handleExtraPVP(chr, attacked);
                }
                chr.getClient().getSession().write(CField.getPVPHPBar(attacked.getId(), attacked.getStat().getHp(), attacked.getStat().getCurrentMaxHp()));
                addedScore += (totalHPLoss / 100) + (totalMPLoss / 100); //ive NO idea
                if (!attacked.isAlive()) {
                    killed = true;
                }

                if (ourAttacks.size() >= mobCount) {
                    break;
                }
            }
        }
        if (killed || addedScore > 0) {
            chr.getEventInstance().addPVPScore(chr, addedScore);
            chr.getClient().getSession().write(CField.getPVPScore(ourScore + addedScore, killed));
        }
        if (didAttack) {
            chr.getMap().broadcastMessage(SummonPacket.pvpSummonAttack(chr.getId(), chr.getLevel(), summon.getObjectId(), summon.isFacingLeft() ? 4 : 0x84, summon.getTruePosition(), ourAttacks));
            if (!summon.isMultiAttack()) {
                chr.getMap().broadcastMessage(SummonPacket.removeSummon(summon, true));
                chr.getMap().removeMapObject(summon);
                chr.removeVisibleMapObject(summon);
                chr.removeSummon(summon);
                if (summon.getSkill() != 35121011) {
                    chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
                }
            }
        }
    }
}
