package com.maris7.team.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;import java.util.regex.*;

public final class Text { private Text(){}
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    public static String color(String s){ if(s==null) return ""; Matcher m=HEX.matcher(s); StringBuffer b=new StringBuffer(); while(m.find()) m.appendReplacement(b, ChatColor.of("#"+m.group(1)).toString()); m.appendTail(b); return ChatColor.translateAlternateColorCodes('&', b.toString()); }
    public static boolean empty(Object v){ if(v==null)return true; if(v instanceof String s)return s.isEmpty(); if(v instanceof List<?> l)return l.isEmpty(); return false; }
    public static ItemStack item(Material mat,String name,List<String> lore){ ItemStack it=new ItemStack(mat); ItemMeta meta=it.getItemMeta(); meta.setDisplayName(ChatColor.RESET + color(name)); List<String> out=new ArrayList<>(); if(lore!=null) for(String l:lore) out.add(ChatColor.RESET + color(l)); meta.setLore(out); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_UNBREAKABLE,ItemFlag.HIDE_DESTROYS,ItemFlag.HIDE_PLACED_ON,ItemFlag.HIDE_ADDITIONAL_TOOLTIP); it.setItemMeta(meta); return it; }
    public static String smallCaps(String in){ String a="abcdefghijklmnopqrstuvwxyz"; String b="ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"; StringBuilder sb=new StringBuilder(); for(char c:in.toLowerCase(Locale.ROOT).toCharArray()){int i=a.indexOf(c); sb.append(i>=0?b.charAt(i):c);} return sb.toString(); }
}
