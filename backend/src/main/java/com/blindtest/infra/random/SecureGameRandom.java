package com.blindtest.infra.random;

import com.blindtest.model.game.GameRandom;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Production game randomness backed by the JDK cryptographic random source. */
@Component
public final class SecureGameRandom implements GameRandom {

    private final SecureRandom random = new SecureRandom();

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
