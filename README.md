# WearSync

Sync BLE smart watch device data (heart rate, battery, etc.) to an InfluxDB server.

### Demo - [gf.hydev.org](https://gf.hydev.org) 🥰

![Screenshot from 2023-01-24 22-53-10](https://user-images.githubusercontent.com/22280294/214479182-c0c5288d-7de3-4e4c-b448-d5b7de75286f.png)

### Setup

1. Setup a InfluxDB Server (You can do it with docker-compose, it's really easy!)
2. Type in your influxDB connection detials in the app

<img src="https://user-images.githubusercontent.com/22280294/214210116-f15b8be4-358c-4d68-a61e-a062e8752ec1.png" width="50%"> 

3. Scan & Connect to your device
4. Watch everything work

<img src="https://user-images.githubusercontent.com/22280294/214210167-ea070cd1-becb-47db-bc0e-b3709958cf45.png" width="50%"> 

5. If you want you can setup grafana to visualize influxdb data like I did

TODO: Upload grafana sample config

### Background Service

For the background service to work, a notification is displayed. To ensure the background service stays alive, please also [disable battery optimizations](https://user-images.githubusercontent.com/22280294/214209600-387f776a-0e37-4ecc-8bbd-03aa17d335db.png).

<img src="docs/imgs/background.png" width="50%"> 

