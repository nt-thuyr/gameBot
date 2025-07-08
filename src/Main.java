import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.*;

import static jsclub.codefest.sdk.algorithm.PathUtils.*;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "113147";
    private static final String PLAYER_NAME = "botable";
    private static final String SECRET_KEY = "sk-9tCiKF60Sxi0KVc1ZtiQdw:mGiTucg2md7pM_jn7C19ZKq_KTUJIhBlnOUYLE5mEgH42V86LMruay6aH7TnYe1m_MmCok6c3KiTWJS0IjkJBg";

    static Node lastChestPosition = null;
    static Obstacle lastChest = null;
    static Player lockedTarget = null;
    static final float maxHealth = 100.0f; // Máu tối đa khởi đầu của player

    static int tickCount = 0;
    static int lootRadius = 2; // Bán kính loot đồ xung quanh player

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                EnemyTrajectoryCollector.collect(gameMap, tickCount);

                float currentHealth = gameMap.getCurrentPlayer().getHealth();
                System.out.println("Current Health: " + currentHealth);

                // 1. Nếu máu yếu, ưu tiên hồi máu
                if (currentHealth < maxHealth * 0.8f) {
                    if (Health.healByAlly(gameMap, hero)) {
                        return; // Nếu đã hồi máu thành công, không làm gì khác
                    } else {
                        Element healingItem = Health.findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
                        if (healingItem != null) {
                            try {
                                hero.useItem(healingItem.getId());
                                System.out.println("Đã sử dụng vật phẩm hồi máu: " + healingItem + ", máu hiện tại: " + currentHealth);
                            } catch (IOException e) {
                                System.out.println("Lỗi khi sử dụng vật phẩm hồi máu: " + e.getMessage());
                            }
                            return;
                        }
                    }
                }

                // 2. Mở rương trong bán kính 2 ô quanh player
                Obstacle targetChest = ItemManager.checkIfHasChest(gameMap);
                if (targetChest != null) {
                    lastChestPosition = new Node(targetChest.getX(), targetChest.getY());
                    lastChest = targetChest;
                    try {
                        ItemManager.openChest(gameMap, hero, targetChest);
                    } catch (IOException e) {
                        System.out.println("Lỗi khi mo ruong: " + e.getMessage());
                    }
                    return; // QUAN TRỌNG: return luôn để không làm gì khác tick này!
                }

                // Nếu vừa phá xong rương, còn item quanh rương thì nhặt
                if (lastChestPosition != null) {
                    lootRadius = 5;
                    if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                        return; // Ưu tiên nhặt item quanh rương, xong mới làm việc khác
                    } else {
                        lastChestPosition = null; // Không còn item quanh, reset
                    }
                }

                // 3. Loot đồ tốt hơn bán kính 2 ô quanh player
                lootRadius = 2;
                if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                    return;
                }

                // Nếu chưa có vũ khí nào trong inventory, đi tìm vũ khí gần nhất
                if (hero.getInventory().getGun() == null && hero.getInventory().getMelee().getId().equals("HAND")
                        && hero.getInventory().getThrowable() == null && hero.getInventory().getSpecial() == null) {
                    try {
                        pickUpNearestWeapon(hero, gameMap, true);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi nhặt vũ khí: " + e.getMessage());
                    }
                    return;
                }

                // 4. Nếu đang lock target thì tấn công cho đến khi tiêu diệt hoặc không còn tấn công được
                if (lockedTarget != null) {
                    // Luôn tìm lại đối tượng player mới nhất theo id
                    Player current = null;
                    for (Player p : gameMap.getOtherPlayerInfo()) {
                        if (p.getId().equals(lockedTarget.getId())) {
                            current = p;
                            break;
                        }
                    }

                    if (current == null || current.getHealth() <= 0) {
                        lockedTarget = null;
                    } else {
                        movementCombo(gameMap, hero, current, currentHealth);
                    }
                }

                // 5. Tìm đối thủ yếu máu nhất để lock target và săn
                Player weakest = Attack.findWeakestPlayer(gameMap);
                if (weakest != null) {
                    lockedTarget = weakest;
                    movementCombo(gameMap, hero, weakest, currentHealth);
                }

                // Nếu không có ai yếu nhất (tức là tất cả máu bằng nhau), chọn người gần nhất
                Player nearest = Attack.findNearestPlayer(gameMap, gameMap.getCurrentPlayer().getPosition());
                if (nearest != null) {
                    lockedTarget = nearest;
                    movementCombo(gameMap, hero, nearest, currentHealth);
                }

                // Nếu không tìm thấy mục tiêu, reset lockedTarget
                lockedTarget = null;

                // 6. (Có thể bổ sung: khám phá map hoặc nhặt vũ khí/support item tốt hơn nếu muốn)
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    public static void movementCombo(GameMap gameMap, Hero hero, Player target, float currentHealth) {
        try {
            boolean attacked = Attack.attackTarget(hero, target, gameMap);
            if (attacked) {
                System.out.println("Đã tấn công target: " + target.getId() + ", máu còn: " + target.getHealth());
                return; // Nếu đã tấn công thành công, không cần làm gì khác
            }

            SupportItem healingItem = Health.findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
            if (currentHealth < maxHealth * 0.8f && healingItem != null) {
                hero.useItem(healingItem.getId());
                return; // Nếu đã sử dụng vật phẩm hồi máu, không cần làm gì khác
            }

            moveToTarget(hero, target, gameMap);

        } catch (IOException | InterruptedException e) {
            System.out.println("Lỗi khi tấn công target (người gần nhất): " + e.getMessage());
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();

        int enemyRange = 1; // Tránh cả vùng quanh enemy

        // Tránh quái vật (dùng trajectory nếu có)
        for (Enemy enemy : gameMap.getListEnemies()) {
            if (enemy.getPosition() == null) continue;

            // Lấy đúng spawnPos ban đầu để xác định trajectory key
            Node spawnPos = EnemyTrajectoryCollector.enemySpawnPos.get(enemy);
            if (spawnPos == null) {
                // Nếu chưa lưu spawnPos, fallback về vị trí hiện tại
                spawnPos = enemy.getPosition();
            }

            Node dangerPos = enemy.getPosition();
            if (EnemyTrajectoryCollector.isReady()) {
                EnemyTrajectoryCollector.TrajectoryInfo info = EnemyTrajectoryCollector.getTrajectory(enemy.getId(), spawnPos);
                if (info != null) {
                    // Dự đoán vị trí enemy ở tick tiếp theo (hoặc +2 nếu muốn)
                    dangerPos = info.getPositionAtTick(tickCount + 1);
                }
            }

            for (int dx = -enemyRange; dx <= enemyRange; dx++) {
                for (int dy = -enemyRange; dy <= enemyRange; dy++) {
                    restrictedNodes.add(new Node(dangerPos.getX() + dx, dangerPos.getY() + dy));
                }
            }
        }

        // Tránh chướng ngại vật
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (obstacle.getPosition() != null) {
                restrictedNodes.add(obstacle.getPosition());
            }
        }

        // Tránh người chơi khác còn sống
        for (Player player : gameMap.getOtherPlayerInfo()) {
            if (player.getPosition() != null && player.getHealth() > 0) {
                restrictedNodes.add(player.getPosition());
            }
        }

        return restrictedNodes;
    }


    // Hàm xác định hướng (direction) từ node hiện tại đến node mục tiêu
    public static String getDirection(Node from, Node to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == 1) return "u";
        if (dx == 0 && dy == -1) return "d";
        if (dx == -1 && dy == 0) return "l";
        if (dx == 1 && dy == 0) return "r";
        // Nếu mục tiêu không kề cạnh, ưu tiên hướng chính
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? "r" : "l";
        } else if (Math.abs(dy) > 0) {
            return dy > 0 ? "u" : "d";
        }
        return "d"; // fallback
    }

    public static void pickUpNearestWeapon(Hero hero, GameMap gameMap, boolean skipDarkArea) throws IOException, InterruptedException {
        List<Weapon> weapons = gameMap.getListWeapons();
        if (weapons == null || weapons.isEmpty()) {
            System.out.println("Không tìm thấy vũ khí trên bản đồ!");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();

        Weapon nearestWeapon = null;
        int minDistance = Integer.MAX_VALUE;
        Node nearestNode = null;

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Weapon weapon : weapons) {
            if (ItemManager.pickupable(hero, weapon)) {
                Node weaponNode = weapon.getPosition();
                if (skipDarkArea && !checkInsideSafeArea(weaponNode, safeZone, mapSize)) continue;
                int dist = distance(currentPosition, weaponNode);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestWeapon = weapon;
                    nearestNode = weaponNode;
                }
            }
        }

        if (nearestWeapon == null) {
            System.out.println("Không tìm thấy vũ khí hợp lệ trong vùng an toàn!");
            return;
        }

        if (distance(currentPosition, nearestNode) > 1) {
            // Nếu không đứng trên vũ khí, di chuyển đến vị trí vũ khí
            System.out.println("Đang di chuyển đến vũ khí gần nhất: " + nearestWeapon.getId() + " tại " + nearestNode);
            moveToTarget(hero, nearestNode, gameMap);
        } else {
            // Nếu đã đứng trên vị trí vũ khí thì nhặt luôn, không di chuyển
            ItemManager.swapItem(gameMap, hero);
            System.out.println("Đang đứng trên vũ khí, thực hiện nhặt.");
        }
    }

    // Di chuyển đến mục tiêu
    public static void moveToTarget(Hero hero, Node targetNode, GameMap gameMap) throws IOException, InterruptedException {
        if (targetNode == null) {
            System.out.println("Target Node không hợp lệ.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        // Tìm đường đi ngắn nhất
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        restrictedNodes.remove(targetNode);
        String path;

        if (gameMap.getElementByIndex(targetNode.x, targetNode.y).getType().equals(ElementType.PLAYER)) {
            path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
        } else {
            path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, true);
        }

        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến mục tiêu: " + gameMap.getElementByIndex(targetNode.x, targetNode.y).getId());
            Main.lockedTarget = null; // Reset mục tiêu nếu không đến được
            return;
        }

        String step = path.substring(0, 1);
        Node positionAfterStep = new Node();

        switch (step) {
            case "u":
                positionAfterStep = new Node(currentPosition.getX(), currentPosition.getY() + 1);
                break;
            case "d":
                positionAfterStep = new Node(currentPosition.getX(), currentPosition.getY() - 1);
                break;
            case "l":
                positionAfterStep = new Node(currentPosition.getX() - 1, currentPosition.getY());
                break;
            case "r":
                positionAfterStep = new Node(currentPosition.getX() + 1, currentPosition.getY());
                break;
            default:
                System.out.println("Hướng di chuyển không hợp lệ: " + step);
                return;
        }
        // Kiểm tra bước di chuyển có nằm trong vùng an toàn không
        if (!checkInsideSafeArea(positionAfterStep, safeZone, mapSize)) {
            System.out.println("Không thể di chuyển ra ngoài vùng an toàn: " + positionAfterStep);
            return;
        }

        hero.move(step);
        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
    }
}