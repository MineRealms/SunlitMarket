package com.society.sunlitmarket.wallet;

import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.deposit.DepositConfig;
import net.minecraft.world.entity.player.Player;

/**
 * Moves money between the physical/bank backend and the virtual wallet at a configured fee.
 *
 * <p>Both directions charge the fee, and the fee is simply never minted - that is the sink that
 * stops the two accounts from being a free laundering loop. Every method returns a short status
 * line for the screen footer, and never throws.
 */
public final class WalletExchange {

    private WalletExchange() {
    }

    public static int fee() {
        return Math.max(0, Math.min(50, DepositConfig.exchangeFeePercent()));
    }

    /** The amount that actually lands after the conversion fee. */
    public static long afterFee(long amount) {
        return amount - amount * fee() / 100L;
    }

    public static String deposit(Player player) {
        if (!DepositConfig.walletEnabled()) return "钱包未启用";
        int available = Currency.cashBalance(player);
        if (available < 0) return "银行账户不可用";
        if (available <= 0) return "没有可存入的货币";
        if (!Currency.payCash(player, available)) return "扣款失败，货币未变动";
        long credited = afterFee(available);
        Wallet.add(player, credited);
        return "存入 " + available + " → 钱包 +" + credited + "（手续费 " + fee() + "%）";
    }

    public static String withdraw(Player player, long amount) {
        if (!DepositConfig.walletEnabled()) return "钱包未启用";
        if (amount <= 0) return "取款金额无效";
        if (amount < DepositConfig.minWithdraw()) return "最低取出 " + DepositConfig.minWithdraw() + " spur";
        if (!Wallet.take(player, amount)) return "钱包余额不足";
        long paid = afterFee(amount);
        try {
            Currency.give(player, (int) Math.min(Integer.MAX_VALUE, paid));
        } catch (RuntimeException ex) {
            Wallet.add(player, amount);
            return "背包放不下 " + paid + " spur，已退回钱包";
        }
        return "钱包 −" + amount + " → 取出 " + paid + "（手续费 " + fee() + "%）";
    }

    /** Withdraws everything currently in the wallet. */
    public static String withdrawAll(Player player) {
        long balance = Wallet.balance(player);
        if (balance <= 0) return "钱包是空的";
        return withdraw(player, balance);
    }
}
