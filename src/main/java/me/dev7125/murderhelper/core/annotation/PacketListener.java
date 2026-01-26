package me.dev7125.murderhelper.core.annotation;

import net.minecraft.network.Packet;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PacketListener {
    Class<? extends Packet> value();
}