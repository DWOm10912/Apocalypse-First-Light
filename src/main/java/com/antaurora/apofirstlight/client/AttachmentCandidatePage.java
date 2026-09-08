package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Safe render copies only; never relocates inventory items. NBT-distinct attachments stay distinct. */
public final class AttachmentCandidatePage {
    public record Entry(int sourceSlot, ItemStack source, int totalCount){}
    public static final int PAGE_SIZE=9;
    public final List<Entry> candidates=new ArrayList<>();
    public int page;
    public void refresh(Inventory inventory,ItemStack gun,NativeAttachment.Slot target){
        candidates.clear();
        for(int i=0;i<36;i++){
            var s=inventory.getItem(i);
            if(s.isEmpty()||!(s.getItem() instanceof NativeAttachment a)||a.slot()!=target||!NativeAttachments.compatible(gun,s))continue;
            int match=-1;
            for(int j=0;j<candidates.size();j++)if(ItemStack.isSameItemSameTags(s,candidates.get(j).source())){match=j;break;}
            if(match<0)candidates.add(new Entry(i,s.copy(),s.getCount()));
            else {var old=candidates.get(match);candidates.set(match,new Entry(old.sourceSlot(),old.source(),old.totalCount()+s.getCount()));}
        }
        page=Math.max(0,Math.min(page,(Math.max(1,candidates.size())-1)/PAGE_SIZE));
    }
    public Entry at(int cell){int index=page*PAGE_SIZE+cell;return index>=0&&index<candidates.size()?candidates.get(index):null;}
}
