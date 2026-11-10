#include "server.h"
#include "patcher.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>

#define SOCKET_NAME "eltrampero_rd"

void server_loop() {
    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    
    // Abstract namespace: primer byte \0
    addr.sun_path[0] = '\0';
    memcpy(addr.sun_path + 1, SOCKET_NAME, strlen(SOCKET_NAME));
    
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCKET_NAME);

    if (bind(srv, (struct sockaddr*)&addr, len) < 0) {
        close(srv);
        return;
    }
    if (listen(srv, 4) < 0) {
        close(srv);
        return;
    }

    for (;;) {
        int cli = accept(srv, nullptr, nullptr);
        if (cli < 0) continue;

        char buf[64] = {0};
        ssize_t n = read(cli, buf, sizeof(buf) - 1);
        if (n <= 0) {
            close(cli);
            continue;
        }
        buf[strcspn(buf, "\r\n")] = '\0';

        char resp[128];
        if (strcmp(buf, "PING") == 0) {
            snprintf(resp, sizeof(resp), "PONG\n");
        } else {
            char* end;
            long idx = strtol(buf, &end, 10);
            if (end != buf && *end == '\0') {
                int r = aplicar_mod((int)idx);
                if (r == 1)      snprintf(resp, sizeof(resp), "[+] Activado\n");
                else if (r == 2) snprintf(resp, sizeof(resp), "VERSION_INCOMPATIBLE\n");
                else             snprintf(resp, sizeof(resp), "PATRON_NO_ENCONTRADO\n");
            } else {
                snprintf(resp, sizeof(resp), "ERROR\n");
            }
        }
        write(cli, resp, strlen(resp));
        close(cli);
    }
}