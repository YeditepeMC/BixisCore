package com.yeditepemc.bixiscore.reward;

/**
 * Bir level'a ait ödül tanımı. Formülle üretilir (bkz. {@link LevelRewards}).
 *
 * @param level      ödülün ait olduğu level (1-50)
 * @param coins      verilecek coin miktarı (Vault üzerinden)
 * @param crateTier  kasa yıldız kademesi (1-5); 0 = kasa yok
 * @param milestone  milestone level mi (10, 20, 30, 40, 50)
 * @param hasTitle   özel unvan/rozet veriliyor mu (yalnızca level 50)
 */
public record LevelReward(int level, long coins, int crateTier, boolean milestone, boolean hasTitle) {

    public boolean hasCrate() {
        return crateTier > 0;
    }
}
