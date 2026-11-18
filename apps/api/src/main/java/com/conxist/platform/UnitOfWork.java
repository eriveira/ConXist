package com.conxist.platform;

import java.util.function.Supplier;

/** Runs work atomically, so a saved change and the events it publishes commit together. */
public interface UnitOfWork {

    <T> T inTransaction(Supplier<T> work);
}
