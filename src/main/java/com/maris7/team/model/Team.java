package com.maris7.team.model;
import java.util.*;
public class Team { public int id; public String name; public UUID leader; public boolean pvp; public String world; public double x,y,z; public float yaw,pitch; public boolean hasHome; public Team(int id,String name,UUID leader,boolean pvp){this.id=id;this.name=name;this.leader=leader;this.pvp=pvp;} }
