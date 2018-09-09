package req;

public enum SpinOffSort {
    TOP(1), RECENT(2);

    private int sortId;

    SpinOffSort(int sortId) {
        this.sortId = sortId;
    }

    public int getSortId() {
        return sortId;
    }
}
