package mcperf.camera;

/** Small, game-independent policy. Commands are issued once, never every tick. */
public final class FlightPolicy {
    public static final String FIRST = "perspective_api.first_person";
    public static final String THIRD = "leawind_third_person.third_person";
    private long flightStart = -1;
    private boolean flying, riding, held, manual;
    private String previous, owned, last;

    public String reset(String current) {
        String restore = owned != null && owned.equals(current) ? previous : null;
        flightStart = -1; flying = riding = held = manual = false;
        previous = owned = last = null;
        return restore;
    }

    public String tick(long now, boolean glide, boolean mount, boolean jump,
                       boolean inputAllowed, String current, String third) {
        boolean rising = jump && !held;
        held = jump;
        boolean wasActive = flying || riding;
        boolean active = glide || mount;
        if (!active) return reset(current);
        if (!wasActive) {
            previous = current; last = current; manual = false; owned = null;
        } else if (last != null && !last.equals(current)) {
            // A normal POV key/wheel action always wins for the rest of this trip.
            manual = true; owned = null;
        }
        boolean beganFlight = glide && !flying;
        if (beganFlight) flightStart = now;
        if (!glide) flightStart = -1;
        flying = glide; riding = mount; last = current;
        if (glide && !beganFlight && rising && inputAllowed) {
            // Space controls this flight, including its landing restoration.
            // Only the normal POV controls opt out of that restoration.
            String target = FIRST.equals(current) ? third : FIRST;
            manual = true; owned = target;
            return command(target);
        }
        if (!manual && inputAllowed && FIRST.equals(current)
                && (mount || (glide && now - flightStart >= 3_000_000_000L))) {
            owned = third;
            return command(third);
        }
        return null;
    }

    private String command(String target) { last = target; return target; }
}
