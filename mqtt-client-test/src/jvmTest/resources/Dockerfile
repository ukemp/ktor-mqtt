FROM eclipse-mosquitto
COPY mosquitto.conf /mosquitto/config/mosquitto.conf
COPY passwd /mosquitto/config/passwd
RUN chmod 700 /mosquitto/config/passwd

# For how to setup self signed certificates see https://mosquitto.org/man/mosquitto-tls-7.html
COPY ca.crt /mosquitto/config/certs/ca.crt
COPY server.key /mosquitto/config/certs/server.key
COPY server.crt /mosquitto/config/certs/server.crt
