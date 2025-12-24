package ch.heigvd.controller;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class MutexAPI {
    public MutexAPI() {}

    public static final Lock LOCK = new ReentrantLock();
}
