package cn.ayaka.omnibattery.client;

import net.minecraft.world.item.ItemStack;

/**
 * 客户端屏幕的"反射桥"。
 * 公共代码（服务端也会加载）**绝不能直接引用客户端类** —— 一旦直接引用，
 * 类加载器在注册阶段就会解析 net.minecraft.client.*，专用服务器没有这些类，
 * 整个注册流程会崩（症状常常表现为"别的模组"注册失败、Suspected Mods: None）。
 */
public final class ClientHooks {
    private ClientHooks() {}

    /** 打开"标签工具自定义值"输入框。 */
    public static void openCustomCap(ItemStack stack) {
        try {
            Class.forName("cn.ayaka.omnibattery.client.ClientScreens")
                    .getMethod("openCustomCap", ItemStack.class).invoke(null, stack);
        } catch (Throwable ignored) {
        }
    }

    /** 打开"某台机器自定义上限"输入框。 */
    public static void openMachineCap(int x, int y, int z, long initial) {
        try {
            Class.forName("cn.ayaka.omnibattery.client.ClientScreens")
                    .getMethod("openMachineCap", int.class, int.class, int.class, long.class)
                    .invoke(null, x, y, z, initial);
        } catch (Throwable ignored) {
        }
    }
}
