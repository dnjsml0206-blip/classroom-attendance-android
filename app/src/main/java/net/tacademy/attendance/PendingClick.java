package net.tacademy.attendance;

import java.time.LocalDate;

/** A saved intention is not proof of a successful UI click or server record. */
record PendingClick(String day,Policy.Phase phase) {
    static PendingClick parse(String value){
        try{String[] parts=value.split(":",-1);if(parts.length!=2)return null;
            String day=LocalDate.parse(parts[0]).toString();Policy.Phase phase=Policy.Phase.valueOf(parts[1]);
            return phase==Policy.Phase.IDLE?null:new PendingClick(day,phase);
        }catch(RuntimeException e){return null;}
    }
    String label(){return day+" · "+(phase==Policy.Phase.ENTRY?"입실":"퇴실");}
}
