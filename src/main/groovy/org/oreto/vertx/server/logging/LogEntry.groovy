package org.oreto.vertx.server.logging

import java.text.SimpleDateFormat

class LogEntry {
    Integer id
    String date
    String thread
    String level
    String className
    String message

    static SimpleDateFormat dateFormat = new SimpleDateFormat('dd MMM yyyy hh:mm:ss,SSS a')

    static LogEntry New(String entry, Integer id) {
        List<String> els = entry.split('[ ]+', 9)
        String dstring = "${els[0]} ${els[1]} ${els[2]} ${els[3]} ${els[4]}"
        //Date d = dateFormat.parse(dstring)
        new LogEntry(
                id: id
                //, dateTime: d
                , date: dstring
                , thread: els[5]?.trim()
                , level: els[6]?.trim()
                , className: els[7]?.trim()
                , message: els[8]?.trim()
        )
    }
}
