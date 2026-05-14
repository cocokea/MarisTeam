# MarisTeam 1.0

Plugin team Bukkit/Folia package `com.maris7.team`.

## Build
- Cài JDK 25.
- Dùng Gradle Wrapper 9.1.0: `./gradlew clean jar`.
- File xuất ra: `build/libs/MarisTeam.jar`.

## Notes
- `plugin.yml` khai báo `folia-supported: true`.
- Spigot libraries loader tải HikariCP 7.0.2, sqlite-jdbc 3.50.3.0, mysql-connector-j 9.3.0.
- Vault và PlaceholderAPI là soft hook/compileOnly.
- Text GUI dùng legacy reset trước tên/lore để tránh italic mặc định.
- Có `config.yml`, `sounds.yml`, `guis/en`, `guis/vi`, `message/message_en.yml`, `message/message_vi.yml`, `message/message_vn.yml`.

## PlaceholderAPI
- `%maristeam_team%`
- `%maristeam_team_smallcap%`
