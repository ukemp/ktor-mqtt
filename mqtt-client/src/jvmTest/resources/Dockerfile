FROM eclipse-mosquitto
COPY mosquitto.conf /mosquitto/config/mosquitto.conf
COPY passwd /mosquitto/config/passwd
RUN chown mosquitto:mosquitto /mosquitto/config/passwd
RUN chmod 0700 /mosquitto/config/passwd

# See https://mosquitto.org/man/mosquitto-tls-7.html on how to setup self signed certificates
COPY ca.crt /mosquitto/config/certs/ca.crt
COPY server.key /mosquitto/config/certs/server.key
COPY server.crt /mosquitto/config/certs/server.crt
