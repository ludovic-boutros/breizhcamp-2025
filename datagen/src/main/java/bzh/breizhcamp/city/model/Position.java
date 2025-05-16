package bzh.breizhcamp.city.model;

import lombok.*;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Position implements Comparable<Position> {
    private int x;
    private int y;

    @Override
    public int compareTo(@NotNull Position o) {
        return Math.max(Math.abs(x - o.x), Math.abs(y - o.y));
    }
}
