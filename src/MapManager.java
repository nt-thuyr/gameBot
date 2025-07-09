import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

import java.util.*;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;

public class MapManager {
    private static final int GRID_SIZE = 10; // Kích thước mỗi ô lưới
    private static final int SAFETY_RADIUS = 10; // Bán kính an toàn

    public static class AreaInfo {
        public int x, y; // Tọa độ trung tâm khu vực
        public int playerCount; // Số lượng người chơi
        public double density; // Mật độ (người chơi/diện tích)
        public List<Player> playersInArea; // Danh sách người chơi trong khu vực
        public boolean hasChest; // Có rương không
        public boolean hasEgg; // Có trứng không
        public double dangerLevel; // Mức độ nguy hiểm (0-1)
        public double distance; // Khoảng cách từ vị trí hiện tại

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

}