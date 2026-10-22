package com.antaurora.apofirstlight.book;

import com.antaurora.apofirstlight.client.book.GuideBookClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Readable books (docs/gameplay/field_survival_guide_v1_design.md): the book files to every client, the client's "stayed
 * on this section" report, and the recipe it taught back to the reader.
 */
public final class GuideBookPackets {
    /** Server to one player: every book file (GuideBooks), replacing the last set. */
    public record Books(List<Entry> books) {
        public record Entry(ResourceLocation id, String json) {}
    }

    /** Client to server: the reader stayed on a section of the book in the main hand long enough to have read it. */
    public record Read(ResourceLocation item, String section) {}

    /** Server to the reader: a recipe the book just taught, by its result item. */
    public record Learned(ResourceLocation result) {}

    /** A book file's longest accepted length in chars (FriendlyByteBuf#readUtf's limit). */
    private static final int MAX_JSON = 1 << 20;

    private GuideBookPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Books.class, (p, b) -> {
            b.writeVarInt(p.books().size());
            for (var e : p.books()) {
                b.writeResourceLocation(e.id());
                b.writeUtf(e.json(), MAX_JSON);
            }
        }, b -> {
            int n = b.readVarInt();
            List<Books.Entry> books = new ArrayList<>(n);
            for (int i = 0; i < n; i++) books.add(new Books.Entry(b.readResourceLocation(), b.readUtf(MAX_JSON)));
            return new Books(List.copyOf(books));
        }, (p, s) -> client(s, () -> GuideBookClient.receive(p.books())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Read.class, (p, b) -> {
            b.writeResourceLocation(p.item());
            b.writeUtf(p.section(), 256);
        }, b -> new Read(b.readResourceLocation(), b.readUtf(256)), (p, s) -> {
            NetworkEvent.Context context = s.get();
            ServerPlayer player = context.getSender();
            if (player != null) context.enqueueWork(() -> {
                if (player.isAlive() && !player.isSpectator()) GuideBooks.read(player, p.item(), p.section());
            });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Learned.class, (p, b) -> b.writeResourceLocation(p.result()), b -> new Learned(b.readResourceLocation()),
                (p, s) -> client(s, () -> GuideBookClient.learned(p.result())), Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }

    private static void client(Supplier<NetworkEvent.Context> supplier, Runnable action) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> action));
        context.setPacketHandled(true);
    }
}
