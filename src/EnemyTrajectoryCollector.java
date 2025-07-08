import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.GameMap;

import java.util.*;

/**
 * Quản lý lịch sử di chuyển, quỹ đạo/chu kỳ của từng cá thể quái vật,
 * kể cả khi chúng có cùng id (phân biệt qua vị trí spawn ban đầu).
 * Hỗ trợ lấy lịch trình enemy để pathfinding tránh bị "đuổi qua lại".
 */
public class EnemyTrajectoryCollector {
    // Key duy nhất cho mỗi enemy: id + vị trí spawn đầu tiên
    static String getEnemyKey(String id, Node startPos) {
        return id + "_" + startPos.getX() + "_" + startPos.getY();
    }

    // Lưu: key -> lịch sử di chuyển
    final Map<String, List<Node>> enemyHistory = new HashMap<>();
    // Lưu vị trí spawn đầu tiên: Enemy reference -> startPos
    final Map<Enemy, Node> enemySpawnPos = new HashMap<>();
    // Đã đủ dữ liệu từng enemy
    final Set<String> completedEnemies = new HashSet<>();
    // key -> thông tin quỹ đạo
    final Map<String, TrajectoryInfo> trajectoryMap = new HashMap<>();
    // Đánh dấu đã đủ dữ liệu cho tất cả enemy
    boolean ready = false;

     static class TrajectoryInfo {
        public final List<Node> path;      // Quỹ đạo (danh sách node đi qua theo thứ tự)
        public final int period;           // Chu kỳ (số tick lặp lại 1 vòng)
        public final int startTickOffset;  // Tick đầu tiên xuất hiện trên path (relative to game start)
        public TrajectoryInfo(List<Node> path, int period, int startTickOffset) {
            this.path = path; this.period = period; this.startTickOffset = startTickOffset;
        }
        // Trả về vị trí enemy ở tick t (so với tick bắt đầu)
        public Node getPositionAtTick(int tick) {
            int idx = (tick - startTickOffset) % period;
            if (idx < 0) idx += period;
            return path.get(idx);
        }
    }

    /**
     * Gọi hàm này mỗi tick đầu game để thu thập lịch sử vị trí enemy.
     * Khi đã đủ dữ liệu, manager sẽ tự động dừng.
     */
     void collect(GameMap gameMap, int currentTick) {
        if (ready) return;
        List<Enemy> enemies = gameMap.getListEnemies();
        // Bước 1: mapping từng cá thể enemy hiện tại với key duy nhất
        for (Enemy enemy : enemies) {
            if (enemy.getPosition() == null) continue;
            Node currentPos = enemy.getPosition();

            // Nếu enemy này đã được ghi nhận spawnPos, lấy lại, nếu chưa thì lưu vị trí spawn
            Node spawnPos = enemySpawnPos.computeIfAbsent(enemy, k -> new Node(currentPos.getX(), currentPos.getY()));
            String key = getEnemyKey(enemy.getId(), spawnPos);

            if (completedEnemies.contains(key)) continue;

            // Lưu vào history nếu khác last position
            List<Node> history = enemyHistory.getOrDefault(key, new ArrayList<>());
            if (history.isEmpty() || !currentPos.equals(history.get(history.size() - 1))) {
                history.add(new Node(currentPos.getX(), currentPos.getY()));
            }
            enemyHistory.put(key, history);

            // Tìm chu kỳ di chuyển (cycle detection)
            int n = history.size();
            if (n >= 4) {
                for (int period = 2; period <= n / 2; period++) {
                    boolean isCycle = true;
                    for (int i = 0; i < period; i++) {
                        if (!history.get(i).equals(history.get(i + period))) {
                            isCycle = false;
                            break;
                        }
                    }
                    if (isCycle) {
                        List<Node> path = new ArrayList<>(history.subList(0, period));
                        trajectoryMap.put(key, new TrajectoryInfo(path, period, currentTick - (n - 1)));
                        completedEnemies.add(key);
                        break;
                    }
                }
            }
        }
        // Kiểm tra đã đủ dữ liệu cho tất cả enemy chưa (dựa trên số enemy cá thể xuất hiện từ đầu)
        if (completedEnemies.size() == enemySpawnPos.size()) {
            ready = true;
            enemyHistory.clear();
        }
    }

    /** Đã đủ dữ liệu cho mọi enemy cá thể? */
    boolean isReady() {
        return ready;
    }

    /** Lấy TrajectoryInfo của enemy theo key (id + spawn pos) */
     TrajectoryInfo getTrajectory(String id, Node spawnPos) {
        return trajectoryMap.get(getEnemyKey(id, spawnPos));
    }

    /** Lấy toàn bộ lịch trình enemy: key -> Info */
     Map<String, TrajectoryInfo> getAllTrajectories() {
        return trajectoryMap;
    }

    /** Lấy key của tất cả enemy đã thu thập */
     Set<String> getAllKeys() {
        return trajectoryMap.keySet();
    }

    /**
     * Hỗ trợ: Lấy key phân biệt các cá thể enemy hiện tại trên map (dùng cho pathfinding)
     */
     static String getKeyForEnemyOnMap(Enemy enemy) {
        Node pos = enemy.getPosition();
        return getEnemyKey(enemy.getId(), pos);
    }
}