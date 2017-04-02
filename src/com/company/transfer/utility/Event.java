package com.company.transfer.utility;

import com.company.transfer.message.Message;

import static com.company.transfer.utility.Event.EventType.IO;

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

    public int priority() {
        if (type == IO || type == EventType.NOT_FOUND) {
            return 3;
        }
        Message m = (Message) data;
        if (m.type == Message.MessageType.UPLOAD_REQUEST ||
                m.type == Message.MessageType.UPLOAD_RESPONSE ||
                m.type == Message.MessageType.DOWNLOAD_REQUEST ||
                m.type == Message.MessageType.DOWNLOAD_RESPONSE) {
            return 2;
        }
        return 1;
    }

    @Override
    public int compareTo(Event<T> e) {
        if (priority() != e.priority()) {
            return -Integer.compare(priority(), e.priority());
        }
        if (e.data instanceof Message && data instanceof Message) {
            return Integer.compare(((Message) data).position, ((Message) e.data).position);
        }
        return Long.compare(time, e.time);
    }


    public enum EventType {
        OUTER, INNER, IO, NOT_FOUND
    }
}
