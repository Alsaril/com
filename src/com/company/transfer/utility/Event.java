package com.company.transfer.utility;

import com.company.transfer.message.Message;

public class Event<T> implements Comparable<Event<T>> {
    public final T data;
    public final EventType type;
    public final long time;

    public Event(T data, EventType type, long time) {
        this.data = data;
        this.type = type;
        this.time = time + System.currentTimeMillis();
    }

    public Event(T data, EventType type) {
        this(data, type, 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Event) {
            Event e = (Event) obj;
            return data.equals(e.data) && type.equals(e.type);
        }
        return false;
    }

    @Override
    public int compareTo(Event<T> e) {
        if (!(data instanceof Message && e.data instanceof Message)) {
            return -Long.compare(time, e.time);
        }
        Message m1 = (Message) data;
        Message m2 = (Message) e.data;
        if (m1.hash.equals(m2.hash)) {
            return m1.block == m2.block ? -Long.compare(time, e.time) : Integer.compare(m1.block, m2.block);
        } else {
            return -Long.compare(time, e.time);
        }
    }


    public enum EventType {
        OUTER, INNER, IO, NOT_FOUND
    }
}
