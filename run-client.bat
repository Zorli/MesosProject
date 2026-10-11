@echo off
:: Script per avviare il client Mesos su Windows
:: Puoi modificare i valori delle variabili qui sotto se l'indirizzo IP cambia.

set SERVER_HOST=172.20.10.3
set TCP_PORT=12345
set RMI_PORT=1099
set CLIENT_RMI_HOST=172.20.10.4

java -DMESOS_SERVER_HOST=%SERVER_HOST% -DMESOS_TCP_PORT=%TCP_PORT% -DMESOS_RMI_PORT=%RMI_PORT% -DMESOS_CLIENT_RMI_HOST=%CLIENT_RMI_HOST% -jar deliverables\jar\mesos-client-windows-x64.jar

pause
