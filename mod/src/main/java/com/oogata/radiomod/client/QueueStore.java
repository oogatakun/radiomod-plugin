package com.oogata.radiomod.client;

import com.oogata.radiomod.net.Messages;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** サーバーから届いた、スロットごとの最新のキューの状態(QUEUE)。プレーヤー画面が読む。 */
public final class QueueStore {

    private static final Map<Messages.Slot, Messages.Queue> QUEUES = new ConcurrentHashMap<>();

    private QueueStore() {
    }

    public static void put(Messages.Queue queue) {
        QUEUES.put(queue.slot(), queue);
    }

    public static Messages.Queue get(Messages.Slot slot) {
        return QUEUES.get(slot);
    }

    public static void clear() {
        QUEUES.clear();
    }
}
