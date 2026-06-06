package com.pixlehavencore.bridge;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * Folia 环境异步区块加载封装。
 * <p>
 * 直接调用 Paper Folia API 的 {@code World.getChunkAtAsync()}，
 * 由调用方通过 {@code CompletableFuture.orTimeout()} 控制超时。
 * <p>
 * 项目仅运行在 Folia 环境，无需 {@code Bukkit.isFolia()} 检测。
 */
public final class FoliaCompat {

    private FoliaCompat() {}

    /**
     * 异步加载区块（Folia 环境）。
     * 直接调用 {@code World.getChunkAtAsync()}，返回异步 Future。
     * 调用方需通过 {@code CompletableFuture.orTimeout()} 设置超时。
     *
     * @param world 目标世界
     * @param x     区块 X 坐标
     * @param z     区块 Z 坐标
     * @return CompletableFuture<Chunk>
     */
    public static CompletableFuture<Chunk> getChunkAtAsync(World world, int x, int z) {
        return world.getChunkAtAsync(x, z);
    }

    /**
     * 异步加载指定坐标所在的区块。
     * 若 {@code location.getWorld()} 为 null，返回已失败的 Future。
     *
     * @param location 目标位置
     * @return CompletableFuture<Chunk>
     */
    public static CompletableFuture<Chunk> getChunkAtAsync(Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("世界不可用"));
        }
        return getChunkAtAsync(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}