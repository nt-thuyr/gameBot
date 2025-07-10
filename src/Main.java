import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.algorithm.PathUtils;
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
    private static final String GAME_ID = "107808";
    private static final String PLAYER_NAME = "botable";
    private static final String SECRET_KEY = "sk-9tCiKF60Sxi0KVc1ZtiQdw:mGiTucg2md7pM_jn7C19ZKq_KTUJIhBlnOUYLE5mEgH42V86LMruay6aH7TnYe1m_MmCok6c3KiTWJS0IjkJBg";

    static Node lastChestPosition = null;
    static Obstacle lastChest = null;
    static Player lockedTarget = null;
    static final float maxHealth = 100.0f; // Máu tối đa khởi đầu của player
    static int lootRadius = 2; // Bán kính loot đồ xung quanh player

    static EnemyTrajectoryCollector collector = new EnemyTrajectoryCollector();
    static String lastPath = null; // Lưu đường đi gần nhất

    static Node retreatTarget = null;
    static int lastDensityCheck = 0;

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                collector.collect(gameMap, gameMap.getStepNumber());

                float currentHealth = gameMap.getCurrentPlayer().getHealth();
                System.out.println("========== Step: " + gameMap.getStepNumber() + " ==========");

                // Phân tích area density định kỳ (mỗi 15 tick)
                if (gameMap.getStepNumber() - lastDensityCheck >= 15) {
                    analyzeAndUpdateStrategy(gameMap, currentHealth);
                    lastDensityCheck = gameMap.getStepNumber();
                }

                // Ưu tiên 1: Sử dụng item hỗ trợ đặc biệt nếu có
//                System.out.println("=== CHECKING PRIORITY 1: USE SPECIAL ITEM ===");
                Health.useSpecialSupportItem(gameMap, hero);

                // Ưu tiên 2: Nếu đang trong chế độ retreat
//                System.out.println("=== CHECKING PRIORITY 2: RETREAT ===");
                System.out.println("Health: " + currentHealth + ". Retreat: " + (retreatTarget != null ? retreatTarget : "Không"));
                if (retreatTarget != null) {
                    if (executeRetreat(gameMap, hero)) {
                        return; // Đã di chuyển, không làm gì khác
                    }
                }

                System.out.println("Số người chơi khác: " + gameMap.getOtherPlayerInfo().size());
                System.out.println("Locked target: " + (lockedTarget != null ? lockedTarget.getId() + " (HP: " + lockedTarget.getHealth() + ")" : "null"));

                // Ưu tiên 3: Nếu máu thấp và ở khu vực không đông người, ưu tiên sử dụng Unicorn Blood
//                System.out.println("=== CHECKING PRIORITY 3: UNICORN BLOOD ===");
                if (currentHealth <= maxHealth * 0.25f && !MapManager.isCurrentAreaCrowded(gameMap, 1)
                        && hero.getInventory().getListSupportItem().stream().anyMatch(item -> item.getId().equals("UNICORN_BLOOD"))) {
                    try {
                        hero.useItem("UNICORN_BLOOD");
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Ưu tiên 4: mở trứng nếu có trứng bất kể vị trí
//                System.out.println("=== CHECKING PRIORITY 4: DRAGON EGG ===");
                Obstacle targetEgg = ItemManager.hasEgg(gameMap);
                if (targetEgg != null && (lockedTarget == null || lockedTarget.getHealth() > currentHealth + 10)) {
                    System.out.println("Đã tìm thấy trứng: " + targetEgg.getId() + ", vị trí: " + targetEgg.getPosition());
                    lastChestPosition = targetEgg.getPosition();
                    lastChest = targetEgg;
                    try {
                        ItemManager.openChest(gameMap, hero, targetEgg);
                        return;
                    } catch (IOException e) {
                        System.out.println("Lỗi khi phá trứng: " + e.getMessage());
                    }
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

                // Ưu tiên 5: tấn công locked target
//                System.out.println("=== CHECKING PRIORITY 5: LOCKED TARGET ===");
                if (lockedTarget != null && (Attack.isCombatReady(hero) ||
                        (lockedTarget.getHealth() <= currentHealth && Attack.currentDamage(hero) >= 15 &&
                                distance(lockedTarget.getPosition(), hero.getGameMap().getCurrentPlayer().getPosition()) <= 5))) {
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
                        movementSet(gameMap, hero, current, currentHealth);
                        return;
                    }
                }


                // Ưu tiên 6: hồi máu nếu máu dưới 80%
//                System.out.println("=== CHECKING PRIORITY 6: HEAL ===");
                if (currentHealth < maxHealth * 0.8f) {
                    if (Health.healByAlly(gameMap, hero)) {
                        return;
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

                // Ưu tiên 7: nếu có kẻ địch ở gần và đủ khả năng tấn công thì tấn công
//                System.out.println("=== CHECKING PRIORITY 7: PLAYER NEARBY ===");
                Player player = Attack.checkIfHasNearbyPlayer(gameMap);
                if (player != null && player.getHealth() <= currentHealth && Attack.currentDamage(hero) >= 15) {
                    lockedTarget = player;
                    movementSet(gameMap, hero, player, currentHealth);
                    return;
                }

                // Ưu tiên 8: mở rương nếu có rương trong phạm vi
//                System.out.println("=== CHECKING PRIORITY 8: CHEST ===");
                Obstacle targetChest = ItemManager.checkIfHasChest(gameMap, 5);
                if (targetChest != null) {
                    lastChestPosition = new Node(targetChest.getX(), targetChest.getY());
                    lastChest = targetChest;
                    try {
                        ItemManager.openChest(gameMap, hero, targetChest);
                    } catch (IOException e) {
                        System.out.println("Lỗi khi mở rương: " + e.getMessage());
                    }
                    return;
                }

                // Ưu tiên 9: loot item tốt hơn xung quanh nếu có
//                System.out.println("=== CHECKING PRIORITY 9: LOOT NEARBY ITEMS ===");
                lootRadius = 3;
                if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                    return;
                }

                // Ưu tiên 10: nếu chỉ số tấn công bé hơn 40, tiếp tục nhặt vũ khí
//                System.out.println("=== CHECKING PRIORITY 10: FIND WEAPON ===");
                if (!Attack.isCombatReady(hero)) {
                    try {
                        if (!ItemManager.pickUpNearestWeapon(hero, gameMap)) {
                            targetChest = ItemManager.checkIfHasChest(gameMap, 15);
                            if (targetChest != null) {
                                lastChestPosition = new Node(targetChest.getX(), targetChest.getY());
                                lastChest = targetChest;
                                try {
                                    ItemManager.openChest(gameMap, hero, targetChest);
                                } catch (IOException e) {
                                    System.out.println("Lỗi khi mở rương: " + e.getMessage());
                                }
                                return;
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi tìm vũ khí gần nhất: " + e.getMessage());
                    }
                }

                // Ưu tiên 11: tấn công người chơi yếu nhất hoặc gần nhất
//                System.out.println("=== CHECKING PRIORITY 11: FIND LOCKED TARGET ===");
                Player weakest = Attack.findWeakestPlayer(gameMap);
                if (weakest != null) {
                    lockedTarget = weakest;
                    movementSet(gameMap, hero, weakest, currentHealth);
                    return;
                }

                // Nếu không có ai yếu nhất (tức là tất cả máu bằng nhau), chọn người gần nhất
                Player nearest = Attack.findNearestPlayer(gameMap, gameMap.getCurrentPlayer().getPosition());
                if (nearest != null) {
                    lockedTarget = nearest;
                    movementSet(gameMap, hero, nearest, currentHealth);
                    return;
                }

                // (Có thể bổ sung: khám phá map hoặc nhặt vũ khí/support item tốt hơn nếu muốn)
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    public static void movementSet(GameMap gameMap, Hero hero, Player target, float currentHealth) {

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

            // Nếu nằm trong tầm của throwable hoặc không có vũ khí cận chiến thì không tiếp tục di chuyển
            Node currentPos = hero.getGameMap().getCurrentPlayer().getPosition();
            Node targetPos = target.getPosition();
            String dir = getDirection(currentPos, targetPos);
            if (Attack.isInsideRange(gameMap, hero.getInventory().getThrowable(), targetPos, currentPos, dir)) {
                return;
            } else {
                System.out.println("Mục tiêu không trong tầm throwable, tiếp tục di chuyển.");
            }

            moveToTarget(hero, target, gameMap);
        } catch (IOException | InterruptedException e) {
            System.out.println("Lỗi khi tấn công target (người gần nhất): " + e.getMessage());
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();
        int enemyRange = 2;
        int futureTickCount = 3;

        // Logic cũ cho enemy và obstacles (giữ nguyên)
        for (Enemy enemy : gameMap.getListEnemies()) {
            if (enemy.getPosition() == null) continue;

            Node spawnPos = EnemyTrajectoryCollector.enemySpawnPos.get(enemy);
            if (spawnPos == null) spawnPos = enemy.getPosition();

            if (collector.isReady()) {
                EnemyTrajectoryCollector.TrajectoryInfo info = collector.getTrajectory(enemy.getId(), spawnPos);
                if (info != null) {
                    for (int future = 1; future <= futureTickCount; future++) {
                        Node dangerPos = info.getPositionAtTick(gameMap.getStepNumber() + future - 1);
                        for (int dx = -enemyRange; dx <= enemyRange; dx++) {
                            for (int dy = -enemyRange; dy <= enemyRange; dy++) {
                                restrictedNodes.add(new Node(dangerPos.getX() + dx, dangerPos.getY() + dy));
                            }
                        }
                    }
                }
            } else {
                int cautiousRange = 2;
                Node dangerPos = enemy.getPosition();
                for (int dx = -cautiousRange; dx <= cautiousRange; dx++) {
                    for (int dy = -cautiousRange; dy <= cautiousRange; dy++) {
                        restrictedNodes.add(new Node(dangerPos.getX() + dx, dangerPos.getY() + dy));
                    }
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
             System.out.println("Không tìm thấy đường đi đến mục tiêu!");
             if (PathUtils.checkInsideSafeArea(targetNode, safeZone, mapSize) && lastPath != null && !lastPath.isEmpty()) {
                 // Lấy bước tiếp theo từ lastPath
                 String nextStep = lastPath.substring(0, 1);
                 Node nextPos = null;
                 switch (nextStep) {
                     case "u":
                         nextPos = new Node(currentPosition.getX(), currentPosition.getY() + 1);
                         break;
                     case "d":
                         nextPos = new Node(currentPosition.getX(), currentPosition.getY() - 1);
                         break;
                     case "l":
                         nextPos = new Node(currentPosition.getX() - 1, currentPosition.getY());
                         break;
                     case "r":
                         nextPos = new Node(currentPosition.getX() + 1, currentPosition.getY());
                         break;
                 }
                 // Kiểm tra xem nextPos có nằm trong restrictedNodes không
                 if (nextPos != null && !restrictedNodes.contains(nextPos)) {
                     path = lastPath;
                 } else {
                     Main.lockedTarget = null;
                     path = null;
                 }
             } else {
                 Main.lockedTarget = null;
                 path = null;
             }
         } else {
             lastPath = path;
         }

         if (path == null || path.isEmpty()) {
             System.out.println("Không có đường đi hợp lệ, dừng di chuyển.");
             return;
         }

         // Check if the move is within the safe zone
         String step = path.substring(0, 1);
                Node positionAfterStep;
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
                        System.out.println("Invalid move direction: " + step);
                        return;
                }

        if (!checkInsideSafeArea(positionAfterStep, safeZone, mapSize)) {
            System.out.println("Cannot move outside safe zone: " + positionAfterStep);

            Element targetElement = gameMap.getElementByIndex(targetNode.x, targetNode.y);
            if (lockedTarget != null && lockedTarget.equals(targetElement)) {
                Weapon gun = hero.getInventory().getGun();
                Weapon melee = hero.getInventory().getMelee();
                Weapon throwable = hero.getInventory().getThrowable();

                if (((gun == null || !Attack.isInsideRange(gameMap, gun, currentPosition, targetNode, getDirection(currentPosition, targetNode))) &&
                        !Attack.isInsideRange(gameMap, melee, currentPosition, targetNode, getDirection(currentPosition, targetNode))) &&
                        (throwable == null || !Attack.isInsideRange(gameMap, throwable, currentPosition, targetNode, getDirection(currentPosition, targetNode)))) {
                    Main.lockedTarget = null; // Reset target if cannot attack
                }

                movementSet(gameMap, hero, Main.lockedTarget, hero.getGameMap().getCurrentPlayer().getHealth());
            } else {
                System.out.println("Cannot attack locked target: " + (lockedTarget != null ? lockedTarget.getId() : "null"));
            }
            return;
        }

        hero.move(step);
        System.out.println("Moved 1 step " + step + " towards target: " + gameMap.getElementByIndex(targetNode.x, targetNode.y).getId());
    }

    // Hàm phân tích và cập nhật chiến thuật
    private static void analyzeAndUpdateStrategy(GameMap gameMap, float currentHealth) {

        boolean isCurrentAreaCrowded = MapManager.isCurrentAreaCrowded(gameMap, 1);

        // Quyết định có nên retreat không
        if (currentHealth < maxHealth * 0.25f && isCurrentAreaCrowded && (lockedTarget == null || lockedTarget.getHealth() > currentHealth)) {
            retreatTarget = MapManager.findSafestNearbyArea(gameMap);
            System.out.println("Chế độ RETREAT: Máu ít + khu vực đông người");
        }
        // Chế độ bình thường
        else {
            retreatTarget = null;
        }
    }

    // Thực hiện retreat
    private static boolean executeRetreat(GameMap gameMap, Hero hero) {
        if (retreatTarget == null) return false;

        Node currentPos = gameMap.getCurrentPlayer().getPosition();
        if (distance(currentPos, retreatTarget) < 5) {
            System.out.println("Đã tới khu vực an toàn, thoát chế độ retreat");
            retreatTarget = null;
            return false;
        }

        // Tìm đường đi tới retreat target
        List<Node> restrictedNodes = getRestrictedNodes(gameMap); // true = avoid crowded areas
        String path = getShortestPath(gameMap, restrictedNodes, currentPos, retreatTarget, true);

        if (path != null && path.length() > 1) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
                System.out.println("Retreat move: " + step);
                return true;
            } catch (Exception e) {
                System.out.println("Lỗi khi retreat: " + e.getMessage());
            }
        }

        return false;
    }
}