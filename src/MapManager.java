import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.io.IOException;
import java.util.*;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;

public class MapManager {
    private static final int GRID_SIZE = 10; // Kích thước mỗi ô lưới
    private static final int SAFETY_RADIUS = 10; // Bán kính an toàn

    public static class AreaInfo {
        public int x, y;            // Tọa độ trung tâm khu vực
        public int playerCount;     // Số lượng người chơi
        public double density;      // Mật độ (người chơi/diện tích)
        public List<Player> playersInArea; // Danh sách người chơi trong khu vực
        public boolean hasChest;    // Có rương không
        public boolean hasEgg;      // Có trứng không
        public double dangerLevel;  // Mức độ nguy hiểm (0-1)
        public double distance;     // Khoảng cách từ vị trí hiện tại

        public AreaInfo(int x, int y) {
            this.x = x;
            this.y = y;
            this.playerCount = 0;
            this.density = 0.0;
            this.playersInArea = new ArrayList<>();
            this.hasChest = false;
            this.hasEgg = false;
            this.dangerLevel = 0.0;
            this.distance = 0.0;
        }
    }

    /**
     * Phân tích toàn bộ map thành các khu vực và tính mật độ
     */
    public static Map<String, AreaInfo> analyzeMapDensity(GameMap gameMap) {
        Map<String, AreaInfo> areaMap = new HashMap<>();
        Node currentPos = gameMap.getCurrentPlayer().getPosition();

        // Lấy kích thước map (giả định 100x100, bạn có thể thay đổi)
        int safeSize = gameMap.getSafeZone();

        // Tạo lưới phân tích
        for (int x = 0; x < safeSize; x += GRID_SIZE) {
            for (int y = 0; y < safeSize; y += GRID_SIZE) {
                String key = x + "," + y;
                AreaInfo area = new AreaInfo(x + GRID_SIZE/2, y + GRID_SIZE/2);

                // Tính khoảng cách từ vị trí hiện tại
                Node areaCenter = new Node(area.x, area.y);
                area.distance = distance(areaCenter, currentPos);

                // Đếm số người chơi trong khu vực
                for (Player player : gameMap.getOtherPlayerInfo()) {
                    if (player.getHealth() > 0 && isPlayerInArea(player, x, y)) {
                        area.playersInArea.add(player);
                        area.playerCount++;
                    }
                }

                // Tính mật độ
                double areaSize = GRID_SIZE * GRID_SIZE;
                area.density = area.playerCount / areaSize;

                // Kiểm tra có rương/trứng không
                area.hasChest = hasChestInArea(gameMap, x, y);
                area.hasEgg = hasEggInArea(gameMap, x, y);

                // Tính mức độ nguy hiểm
                area.dangerLevel = calculateDangerLevel(area, gameMap);

                areaMap.put(key, area);
            }
        }

        return areaMap;
    }

    /**
     * Tìm khu vực ít người và gần nhất (để retreat)
     */
    public static Node findSafestNearbyArea(GameMap gameMap) {
        Map<String, AreaInfo> areaMap = analyzeMapDensity(gameMap);

        AreaInfo bestArea = null;
        double bestScore = -1;

        for (AreaInfo area : areaMap.values()) {
            // Chỉ xét khu vực không quá xa
            if (area.distance > 20) continue;

            double score = 0;

            // Điểm thưởng cho khu vực ít người
            if (area.playerCount == 0) score += 20;
            else if (area.playerCount == 1) score += 10;
            else score -= area.playerCount * 5;

            // Trừ điểm cho khoảng cách (ưu tiên gần hơn)
            score -= area.distance * 0.1;

            // Trừ điểm cho mức độ nguy hiểm
            score -= area.dangerLevel * 15;

            // Bonus nhỏ cho có rương/trứng (nhưng không ưu tiên lắm khi retreat)
            if (area.hasChest) score += 2;
            if (area.hasEgg) score += 3;

            if (score > bestScore) {
                bestScore = score;
                bestArea = area;
            }
        }

        return bestArea != null ? new Node(bestArea.x, bestArea.y) : null;
    }

    /**
     * Kiểm tra khu vực hiện tại có đông người không
     */
    public static boolean isCurrentAreaCrowded(GameMap gameMap, int numberOfPlayers) {
        Node currentPos = gameMap.getCurrentPlayer().getPosition();

        int nearbyPlayers = 0;
        for (Player player : gameMap.getOtherPlayerInfo()) {
            if (player.getHealth() <= 0) continue;
            if (distance(currentPos, player.getPosition()) < SAFETY_RADIUS) {
                nearbyPlayers++;
            }
        }

        return nearbyPlayers >= numberOfPlayers;
    }

    /**
     * Kiểm tra có nên tấn công player này không (dựa trên mật độ xung quanh)
     */
    public static boolean shouldEngagePlayer(GameMap gameMap, Player target) {
        // Đếm số người xung quanh target
        int enemiesNearTarget = 0;
        for (Player player : gameMap.getOtherPlayerInfo()) {
            if (player.getId().equals(target.getId()) || player.getHealth() <= 0) continue;
            if (distance(target.getPosition(), player.getPosition()) < SAFETY_RADIUS) {
                enemiesNearTarget++;
            }
        }

        // Không tấn công nếu có người xung quanh target
        return enemiesNearTarget < 2;
    }

    /**
     * Tìm khu vực đông người
     */
    public static List<Node> getCrowdedNodes(GameMap gameMap) {
        List<Node> crowdedNodes = new ArrayList<>();

        // Tìm tất cả khu vực đông người
        for (Player player : gameMap.getOtherPlayerInfo()) {
            if (player.getHealth() <= 0) continue;

            Node playerPos = player.getPosition();

            // Đếm số người xung quanh player này
            int nearbyCount = 0;
            for (Player other : gameMap.getOtherPlayerInfo()) {
                if (other.getId().equals(player.getId()) || other.getHealth() <= 0) continue;
                if (distance(playerPos, other.getPosition()) < SAFETY_RADIUS) {
                    nearbyCount++;
                }
            }

            // Nếu khu vực đông người (2+ người xung quanh), thêm vào restricted
            if (nearbyCount >= 3) {
                for (int dx = -10; dx <= 10; dx++) {
                    for (int dy = -10; dy <= 10; dy++) {
                        crowdedNodes.add(new Node(playerPos.getX() + dx, playerPos.getY() + dy));
                    }
                }
            }
        }

        return crowdedNodes;
    }

    // Các helper methods
    private static boolean isPlayerInArea(Player player, int areaX, int areaY) {
        Node playerPos = player.getPosition();
        return playerPos.getX() >= areaX && playerPos.getX() < areaX + MapManager.GRID_SIZE &&
                playerPos.getY() >= areaY && playerPos.getY() < areaY + MapManager.GRID_SIZE;
    }

    private static boolean hasChestInArea(GameMap gameMap, int areaX, int areaY) {
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (obstacle.getId().equals("CHEST") &&
                    obstacle.getX() >= areaX && obstacle.getX() < areaX + MapManager.GRID_SIZE &&
                    obstacle.getY() >= areaY && obstacle.getY() < areaY + MapManager.GRID_SIZE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEggInArea(GameMap gameMap, int areaX, int areaY) {
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (obstacle.getId().equals("DRAGON_EGG") &&
                    obstacle.getX() >= areaX && obstacle.getX() < areaX + MapManager.GRID_SIZE &&
                    obstacle.getY() >= areaY && obstacle.getY() < areaY + MapManager.GRID_SIZE) {
                return true;
            }
        }
        return false;
    }

    private static double calculateDangerLevel(AreaInfo area, GameMap gameMap) {
        double danger = 0.0;

        // Mật độ người chơi
        danger += area.density * 1000;

        // Số lượng người chơi tuyệt đối
        danger += area.playerCount * 0.3;

        // Kiểm tra có người chơi mạnh không
        for (Player player : area.playersInArea) {
            if (player.getHealth() > 80) {
                danger += 0.2;
            }
        }

        // Kiểm tra có enemy xung quanh không
        for (Enemy enemy : gameMap.getListEnemies()) {
            if (enemy.getPosition() != null) {
                Node areaCenter = new Node(area.x, area.y);
                if (distance(areaCenter, enemy.getPosition()) < SAFETY_RADIUS) {
                    danger += 0.5; // Mức độ nguy hiểm cao nếu có enemy gần
                }
            }
        }

        return Math.min(danger, 1.0);
    }

     static void moveToSafePoint(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);

        // Tìm điểm trung tâm map (ưu tiên vào giữa bo)
        int center = mapSize / 2;

        // Tìm tất cả các node nằm trong bo, không bị restricted, sắp xếp theo khoảng cách tới center tăng dần
        List<Node> candidates = new ArrayList<>();
        for (int x = safeZone; x < mapSize - safeZone; x++) {
            for (int y = safeZone; y < mapSize - safeZone; y++) {
                Node n = new Node(x, y);
                if (!restrictedNodes.contains(n)) {
                    candidates.add(n);
                }
            }
        }

        // Sắp xếp theo khoảng cách tới center tăng dần
        candidates.sort(Comparator.comparingInt(n -> Math.abs(n.getX() - center) + Math.abs(n.getY() - center)));

        // Chọn candidate gần nhất center và gần current nhất
        Node best = null;
        int minDist = Integer.MAX_VALUE;
        for (Node c : candidates) {
            int d = distance(currentPosition, c);
            if (d < minDist) {
                minDist = d;
                best = c;
            }
            // Nếu ở ngay trên node an toàn thì break luôn
            if (d == 0) {
                best = c;
                break;
            }
        }

        if (best != null) {
            System.out.println("Đang ở ngoài bo, di chuyển đến điểm an toàn gần trung tâm: " + best);
            Main.moveToTarget(hero, best, gameMap);
        } else {
            System.out.println("Không tìm thấy điểm an toàn nào phù hợp trong bo!");
        }
    }

    private static Map<String, Float> lastHpMap = new HashMap<>();

    /**
     * Nhận biết player yếu máu trong các cặp giao chiến ở gần.
     * Nếu chỉ còn 1 người yếu máu ở gần, cũng trả về để ưu tiên tấn công.
     */
    public static List<Player> detectCombatAndFinishTargets(GameMap gameMap, float lowHpThreshold, int pairRange) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        List<Player> finishTargets = new ArrayList<>();
        if (players == null || players.isEmpty()) return finishTargets;

        // Cập nhật lịch sử máu
        for (Player p : players) {
            lastHpMap.put(p.getId(), p.getHealth());
        }

        // Xử lý các cặp giao chiến
        Set<String> added = new HashSet<>(); // Tránh trùng lặp
        for (int i = 0; i < players.size(); i++) {
            Player p1 = players.get(i);
            if (p1.getHealth() <= 0) continue;
            for (int j = i + 1; j < players.size(); j++) {
                Player p2 = players.get(j);
                if (p2.getHealth() <= 0) continue;
                int dist = distance(p1.getPosition(), p2.getPosition());
                if (dist <= pairRange) {
                    Float hp1Old = lastHpMap.get(p1.getId());
                    Float hp2Old = lastHpMap.get(p2.getId());
                    boolean p1LosingHp = hp1Old != null && p1.getHealth() < hp1Old;
                    boolean p2LosingHp = hp2Old != null && p2.getHealth() < hp2Old;

                    if (p1LosingHp || p2LosingHp) {
                        boolean p1Weak = p1.getHealth() <= lowHpThreshold;
                        boolean p2Weak = p2.getHealth() <= lowHpThreshold;
                        if (p1Weak && !added.contains(p1.getId())) {
                            finishTargets.add(p1);
                            added.add(p1.getId());
                            System.out.println("[MapManager] Player yếu máu trong giao tranh: " + p1.getId());
                        }
                        if (p2Weak && !added.contains(p2.getId())) {
                            finishTargets.add(p2);
                            added.add(p2.getId());
                            System.out.println("[MapManager] Player yếu máu trong giao tranh: " + p2.getId());
                        }
                    }
                }
            }
        }

        // Nếu không phát hiện cặp giao tranh, kiểm tra trường hợp chỉ còn 1 người yếu máu ở gần
        if (finishTargets.isEmpty() && players.size() == 1) {
            Player p = players.get(0);
            if (p.getHealth() > 0 && p.getHealth() <= lowHpThreshold) {
                finishTargets.add(p);
                System.out.println("[MapManager] Chỉ còn 1 player yếu máu: " + p.getId());
            }
        }

        return finishTargets;
    }
}