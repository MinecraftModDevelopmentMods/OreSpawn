package zone.moddev.mc.orespawn.commands.admin;

/** A confirmation is short-lived and cannot be borrowed by another operator. */
final class PreviewLease {
    static final long LIFETIME = 120000000000L;
    private final String owner;
    private final String profile;
    private final long issued;
    PreviewLease(String owner, String profile, long issued) { this.owner = owner; this.profile = profile; this.issued = issued; }
    boolean expired(long now) { return now - issued >= LIFETIME || now - issued < 0; }
    boolean accepts(String operator, String currentProfile, long now) {
        return owner.equals(operator) && profile.equals(currentProfile) && !expired(now);
    }
}
