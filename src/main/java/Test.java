import java.util.Random;

class Node {
    int value;
    Node[] forward;

    public Node(int value, int level) {
        this.value = value;
        this.forward = new Node[level + 1];
    }
}

class SkipList {
    private static final int MAX_LEVEL = 4;
    private Node head = new Node(-1, MAX_LEVEL);
    private Random random = new Random();

    private int getRandomLevel() {
        int level = 0;
        while (random.nextBoolean() && level < MAX_LEVEL) {
            level++;
        }
        return level;
    }

    public void insert(int value) {
        Node[] update = new Node[MAX_LEVEL + 1];
        Node current = head;

        for (int i = MAX_LEVEL; i >= 0; i--) {
            while (current.forward[i] != null && current.forward[i].value < value) {
                current = current.forward[i];
            }
            update[i] = current;
        }

        int level = getRandomLevel();
        Node newNode = new Node(value, level);
        for (int i = 0; i <= level; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
    }
}

public class Test {
    public static void main(String[] args) {
        SkipList skipList = new SkipList();
        skipList.insert(3);
        skipList.insert(7);
        skipList.insert(1);
        skipList.insert(5);

        System.out.println("Insertion completed!");
    }
}