import { useEffect, useMemo, useState } from 'react';
import { Animated, Dimensions, Easing, StyleSheet, View } from 'react-native';

const BALLOON_COLORS = ['#F43F5E', '#F59E0B', '#22C55E', '#3B82F6', '#A855F7', '#EC4899'];
const CONFETTI_COLORS = ['#6B5BFF', '#F59E0B', '#22C55E', '#F43F5E', '#38BDF8'];

const SCREEN_HEIGHT = Dimensions.get('window').height;
const SCREEN_WIDTH = Dimensions.get('window').width;

interface Particle {
  key: string;
  left: number;
  size: number;
  drift: number;
  duration: number;
  color: string;
  shape: 'balloon' | 'square' | 'strip';
  spin: number;
}

/** Stable scatter: same layout every render, no re-randomising mid-flight. */
function pseudoRandom(seed: number) {
  const x = Math.sin(seed) * 10000;
  return x - Math.floor(x);
}

function buildParticles(count: number, seed: number): Particle[] {
  const particles: Particle[] = [];
  for (let i = 0; i < count; i++) {
    const r1 = pseudoRandom(seed + i * 1.7);
    const r2 = pseudoRandom(seed + 100 + i * 2.3);
    const r3 = pseudoRandom(seed + 200 + i * 3.1);
    const shape: Particle['shape'] = r3 > 0.72 ? 'square' : r3 < 0.18 ? 'strip' : 'balloon';
    particles.push({
      key: `${seed}-${i}`,
      left: r1 * SCREEN_WIDTH,
      size: 10 + r2 * 12,
      drift: (r3 - 0.5) * 90,
      duration: 4200 + r1 * 3200,
      color: (shape === 'balloon' ? BALLOON_COLORS : CONFETTI_COLORS)[i % 6],
      shape,
      spin: (r1 - 0.5) * 720,
    });
  }
  return particles;
}

interface WinnerBalloonsProps {
  /** Bump to replay the burst, e.g. every time the winner modal opens. */
  runKey?: number | string;
  count?: number;
}

/**
 * Balloons and confetti for a winner. Pure core `Animated` on the native driver:
 * no worklets, no layout reads, and it runs the same on web.
 */
export function WinnerBalloons({ runKey = 0, count = 18 }: WinnerBalloonsProps) {
  const [progress] = useState(() => new Animated.Value(0));
  const particles = useMemo(() => buildParticles(count, 7), [count]);

  useEffect(() => {
    progress.setValue(0);
    const animation = Animated.loop(
      Animated.timing(progress, {
        toValue: 1,
        // Every particle crosses the screen in the same window; each is offset by
        // its own drift and size, so the burst reads as scattered.
        duration: particles[0]?.duration ?? 6000,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );
    animation.start();
    return () => animation.stop();
  }, [particles, progress, runKey]);

  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { overflow: 'hidden' }]}>
      {particles.map((p) => {
        const translateY = progress.interpolate({
          inputRange: [0, 1],
          outputRange: [-120, SCREEN_HEIGHT + 120],
        });
        const translateX = progress.interpolate({
          inputRange: [0, 0.5, 1],
          outputRange: [0, p.drift, 0],
        });
        const rotate = progress.interpolate({
          inputRange: [0, 1],
          outputRange: ['0deg', `${p.spin}deg`],
        });
        return (
          <Animated.View
            key={p.key}
            style={{
              position: 'absolute',
              top: 0,
              left: p.left,
              opacity: progress.interpolate({
                inputRange: [0, 0.08, 0.85, 1],
                outputRange: [0, 1, 1, 0],
              }),
              transform: [{ translateY }, { translateX }, { rotate }],
            }}
          >
            {p.shape === 'balloon' ? (
              <View className="items-center">
                <View
                  style={{
                    width: p.size,
                    height: p.size * 1.25,
                    borderRadius: p.size,
                    backgroundColor: p.color,
                  }}
                />
                <View
                  style={{
                    width: 1.5,
                    height: p.size * 0.9,
                    backgroundColor: p.color,
                    opacity: 0.7,
                  }}
                />
              </View>
            ) : (
              <View
                style={{
                  width: p.shape === 'strip' ? p.size * 0.45 : p.size * 0.8,
                  height: p.shape === 'strip' ? p.size * 1.8 : p.size * 0.8,
                  backgroundColor: p.color,
                }}
              />
            )}
          </Animated.View>
        );
      })}
    </View>
  );
}
