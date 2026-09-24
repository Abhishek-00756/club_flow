package com.clubflow.user;

public enum Role {
    SUPERADMIN(5), ADMIN(4), SECRETARY(3), JOINT_SECRETARY(2), MEMBER(1);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean outranks(Role other) {
        return rank > other.rank;
    }
}
