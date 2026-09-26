package com.exteragram.messenger.plugins;

/**
 * PrimeGram: exteraGram's application lifecycle event, as plugins look it up by name
 * ({@code find_class("com.exteragram.messenger.plugins.AppEvent")}) to compare against the event
 * {@code on_app_event} receives. The event object PrimeGram hands over is base_plugin's own
 * AppEvent, which compares equal to these constants by name - see base_plugin.py.
 */
public enum AppEvent {
    START,
    STOP,
    PAUSE,
    RESUME
}
