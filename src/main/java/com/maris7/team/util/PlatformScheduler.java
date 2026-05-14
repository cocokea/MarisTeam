package com.maris7.team.util;

import org.bukkit.Bukkit;import org.bukkit.Location;import org.bukkit.entity.Entity;import org.bukkit.plugin.Plugin;import java.lang.reflect.Method;import java.util.logging.Level;

public final class PlatformScheduler { private final Plugin plugin; private final boolean folia;
 public PlatformScheduler(Plugin plugin){this.plugin=plugin; this.folia=classExists("io.papermc.paper.threadedregions.RegionizedServer");}
 private boolean classExists(String n){try{Class.forName(n);return true;}catch(Throwable t){return false;}}
 public boolean isFolia(){return folia;}
 public void async(Runnable r){ if(folia){ if(!reflectScheduler("getAsyncScheduler", r)) warn("async scheduler"); } else Bukkit.getScheduler().runTaskAsynchronously(plugin,r); }
 public void global(Runnable r){ if(folia){ if(!reflectScheduler("getGlobalRegionScheduler", r)) warn("global region scheduler"); } else Bukkit.getScheduler().runTask(plugin,r); }
 public void entity(Entity e,Runnable r){ if(folia){ try{Object sch=e.getClass().getMethod("getScheduler").invoke(e); Method run=sch.getClass().getMethod("run",Plugin.class,java.util.function.Consumer.class,Runnable.class); run.invoke(sch,plugin,(java.util.function.Consumer<Object>)t->r.run(),null); return;}catch(Throwable ex){warn("entity scheduler", ex);} } global(r); }
 public void entityDelayed(Entity e,Runnable r,long ticks){ if(folia){ try{Object sch=e.getClass().getMethod("getScheduler").invoke(e); Method run=sch.getClass().getMethod("runDelayed",Plugin.class,java.util.function.Consumer.class,Runnable.class,long.class); run.invoke(sch,plugin,(java.util.function.Consumer<Object>)t->r.run(),null,ticks); return;}catch(Throwable ex){warn("entity delayed scheduler", ex);} } Bukkit.getScheduler().runTaskLater(plugin,r,ticks); }
 public void teleport(Entity e,Location l){ if(folia){ try{e.getClass().getMethod("teleportAsync",Location.class).invoke(e,l); return;}catch(Throwable ex){warn("async teleport", ex);} } entity(e,()->e.teleport(l)); }
 public void region(Location l,Runnable r){ if(folia){ try{Object rs=Bukkit.class.getMethod("getRegionScheduler").invoke(null); Method run=rs.getClass().getMethod("run",Plugin.class,Location.class,java.util.function.Consumer.class); run.invoke(rs,plugin,l,(java.util.function.Consumer<Object>)t->r.run()); return;}catch(Throwable ex){warn("region scheduler", ex);} } global(r); }
 private boolean reflectScheduler(String getter,Runnable r){
  try{
   Object s=Bukkit.class.getMethod(getter).invoke(null);
   java.util.function.Consumer<Object> task=t->r.run();
   Method run=findSchedulerMethod(s.getClass(), "runNow");
   if(run==null) run=findSchedulerMethod(s.getClass(), "run");
   if(run==null) throw new NoSuchMethodException(getter + " runNow/run(Plugin, Consumer)");
   run.invoke(s,plugin,task);
   return true;
  }catch(Throwable t){warn(getter, t); return false;}
 }
 private Method findSchedulerMethod(Class<?> type,String name){
  for(Method m:type.getMethods()){
   Class<?>[] p=m.getParameterTypes();
   if(m.getName().equals(name)&&p.length==2&&Plugin.class.isAssignableFrom(p[0])&&java.util.function.Consumer.class.isAssignableFrom(p[1])) return m;
  }
  return null;
 }
 private void warn(String target){plugin.getLogger().warning("Could not dispatch task on Folia " + target + ". Task was not run.");}
 private void warn(String target,Throwable t){plugin.getLogger().log(Level.WARNING,"Could not dispatch task on Folia " + target + ".",t);}
}
