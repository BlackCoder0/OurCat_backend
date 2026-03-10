package com.ourcat.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class WeatherService {

    @Value("${ourcat.qweather.api-host:https://api.qweather.com}")
    private String apiHost;

    @Value("${ourcat.qweather.jwt:}")
    private String jwt;

    @Value("${ourcat.qweather.jwt-file:}")
    private String jwtFile;

    @Value("${ourcat.open-meteo.base-url:https://api.open-meteo.com}")
    private String openMeteoBaseUrl;

    @Value("${ourcat.open-meteo.model:cma_grapes_global}")
    private String openMeteoModel;

    @Value("${ourcat.open-meteo.timezone:Asia/Shanghai}")
    private String openMeteoTimezone;

    @Value("${ourcat.open-meteo.temp-change-threshold:8}")
    private int tempChangeThreshold;

    @Value("${ourcat.open-meteo.heavy-precip-threshold:30}")
    private double heavyPrecipThreshold;

    private final RestTemplate restTemplate = new RestTemplate();

    private String resolveJwt() {
        if (jwtFile != null && !jwtFile.isEmpty()) {
            try {
                Path path = Paths.get(jwtFile.trim());
                if (Files.isRegularFile(path)) {
                    String fromFile = Files.readString(path).trim();
                    if (!fromFile.isEmpty()) {
                        return fromFile.split("\\s")[0];
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (jwt != null && !jwt.isEmpty()) {
            return jwt.trim();
        }
        return null;
    }

    private String baseUrl() {
        return (apiHost != null && !apiHost.isEmpty()) ? apiHost.replaceAll("/$", "") : "https://api.qweather.com";
    }

    private org.springframework.http.HttpHeaders authHeaders() {
        String token = resolveJwt();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * 预警：和风 v7/warning/now 仅负责官方灾害预警；气温骤变与强降水补充提醒由 Open-Meteo（CMA
     * GRAPES）日预报按用户位置计算。
     */
    @SuppressWarnings("unchecked")
    public Optional<List<String>> getWarnings(double lat, double lng) {
        List<String> out = new ArrayList<>();

        String token = resolveJwt();
        if (token != null && !token.isEmpty()) {
            String base = baseUrl();
            String location = lng + "," + lat;
            try {
                String url = base + "/v7/warning/now?location=" + location;
                org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(
                        authHeaders());
                org.springframework.http.ResponseEntity<Map> respEntity = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, entity, Map.class);
                Map<String, Object> resp = respEntity.getBody();
                if (resp != null && "200".equals(resp.get("code"))) {
                    Object warning = resp.get("warning");
                    if (warning instanceof List) {
                        for (Map<String, Object> m : (List<Map<String, Object>>) warning) {
                            Object text = m.get("text");
                            if (text != null && !text.toString().isEmpty())
                                out.add(text.toString());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        try {
            String base = (openMeteoBaseUrl != null && !openMeteoBaseUrl.isEmpty())
                    ? openMeteoBaseUrl.replaceAll("/$", "")
                    : "https://api.open-meteo.com";
            String url = base + "/v1/forecast?latitude=" + lat + "&longitude=" + lng
                    + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum"
                    + "&timezone=" + (openMeteoTimezone != null ? openMeteoTimezone : "Asia/Shanghai")
                    + "&forecast_days=3&models=" + (openMeteoModel != null ? openMeteoModel : "cma_grapes_global");
            org.springframework.http.ResponseEntity<Map> r = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = r.getBody();
            if (body != null && body.get("daily") instanceof Map) {
                Map<String, Object> daily = (Map<String, Object>) body.get("daily");
                List<?> timeList = (List<?>) daily.get("time");
                List<?> maxList = (List<?>) daily.get("temperature_2m_max");
                List<?> precipList = (List<?>) daily.get("precipitation_sum");
                if (timeList != null && maxList != null && maxList.size() >= 2) {
                    double max0 = toDouble(maxList.get(0), 0);
                    double max1 = toDouble(maxList.get(1), 0);
                    int diff = (int) Math.round(max1 - max0);
                    if (diff >= tempChangeThreshold) {
                        out.add("【气温提醒】明日较今日显著升温，请注意防暑降温。");
                    } else if (diff <= -tempChangeThreshold) {
                        out.add("【气温提醒】明日较今日显著降温，请注意添衣保暖。");
                    }
                }
                if (precipList != null && !precipList.isEmpty()) {
                    double precip = toDouble(precipList.get(0), 0);
                    if (precip >= heavyPrecipThreshold) {
                        out.add("【降水提醒】今日预报有强降水（约" + (int) precip + "mm），请注意防范。");
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return Optional.of(out);
    }

    /**
     * 今日天气文案：使用 Open-Meteo（CMA GRAPES）逐小时数据，取当前时刻。
     */
    @SuppressWarnings("unchecked")
    public Optional<String> getTodayWeatherText(double lat, double lng) {
        try {
            String base = (openMeteoBaseUrl != null && !openMeteoBaseUrl.isEmpty())
                    ? openMeteoBaseUrl.replaceAll("/$", "")
                    : "https://api.open-meteo.com";
            String tz = openMeteoTimezone != null ? openMeteoTimezone : "Asia/Shanghai";
            String url = base + "/v1/forecast?latitude=" + lat + "&longitude=" + lng
                    + "&hourly=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m"
                    + "&timezone=" + tz + "&forecast_days=1&models="
                    + (openMeteoModel != null ? openMeteoModel : "cma_grapes_global");
            org.springframework.http.ResponseEntity<Map> r = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = r.getBody();
            if (body == null || !(body.get("hourly") instanceof Map))
                return Optional.empty();
            Map<String, Object> hourly = (Map<String, Object>) body.get("hourly");
            List<?> times = (List<?>) hourly.get("time");
            List<?> temps = (List<?>) hourly.get("temperature_2m");
            List<?> apparences = (List<?>) hourly.get("apparent_temperature");
            List<?> humidities = (List<?>) hourly.get("relative_humidity_2m");
            List<?> codes = (List<?>) hourly.get("weather_code");
            List<?> windSpeeds = (List<?>) hourly.get("wind_speed_10m");
            List<?> windDirs = (List<?>) hourly.get("wind_direction_10m");
            if (times == null || temps == null || times.isEmpty())
                return Optional.empty();
            int index = 0;
            try {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(tz));
                index = cal.get(Calendar.HOUR_OF_DAY);
                if (index >= times.size())
                    index = times.size() - 1;
            } catch (Exception e) {
            }
            String textDesc = wmoCodeToChinese(codes != null && index < codes.size() ? toInt(codes.get(index), 0) : 0);
            double temp = temps != null && index < temps.size() ? toDouble(temps.get(index), 0) : 0;
            double feels = apparences != null && index < apparences.size() ? toDouble(apparences.get(index), temp)
                    : temp;
            int humidity = humidities != null && index < humidities.size()
                    ? (int) Math.round(toDouble(humidities.get(index), 0))
                    : 0;
            double windSpeed = windSpeeds != null && index < windSpeeds.size() ? toDouble(windSpeeds.get(index), 0) : 0;
            int windDir = windDirs != null && index < windDirs.size()
                    ? (int) Math.round(toDouble(windDirs.get(index), 0))
                    : 0;
            String windDirStr = windDirectionToChinese(windDir);

            StringBuilder sb = new StringBuilder();
            sb.append("今日天气：").append(textDesc);
            sb.append("，气温 ").append(String.format("%.0f", temp)).append("°C");
            sb.append("，体感 ").append(String.format("%.0f", feels)).append("°C");
            if (!windDirStr.isEmpty())
                sb.append("，").append(windDirStr);
            sb.append(" ").append(String.format("%.0f", windSpeed)).append(" km/h");
            if (humidity > 0)
                sb.append("，湿度 ").append(humidity).append("%");
            sb.append("。（数据来源：中国气象局 CMA GRAPES）");
            return Optional.of(sb.toString());
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static String wmoCodeToChinese(int code) {
        if (code == 0)
            return "晴";
        if (code == 1)
            return "少云";
        if (code == 2)
            return "多云";
        if (code == 3)
            return "阴";
        if (code == 45 || code == 48)
            return "雾";
        if (code >= 51 && code <= 57)
            return "毛毛雨";
        if (code >= 61 && code <= 67)
            return "雨";
        if (code >= 71 && code <= 77)
            return "雪";
        if (code >= 80 && code <= 82)
            return "阵雨";
        if (code >= 85 && code <= 86)
            return "阵雪";
        if (code >= 95 && code <= 99)
            return "雷雨";
        return "多云";
    }

    private static String windDirectionToChinese(int degrees) {
        if (degrees < 0)
            degrees = 0;
        if (degrees >= 360)
            degrees = 359;
        String[] dirs = { "北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风" };
        int i = (int) Math.round(degrees / 45.0) % 8;
        return dirs[i];
    }

    private static double toDouble(Object o, double def) {
        if (o == null)
            return def;
        if (o instanceof Number)
            return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static int toInt(Object o, int def) {
        if (o == null)
            return def;
        if (o instanceof Number)
            return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
