package com.pikachew.dangUBI.domain.score.model;

/**
 * 이벤트 묶음별 가중치 w (docs/score-spec.md 3장). 감점 = Σ w × (지표 / D km).
 *
 * @param overspeed 과속거리 1km당
 * @param accel     급가속 1회당
 * @param brake     급감속 1회당
 * @param turn      급회전 1회당
 */
public record ScoreWeights(
		double overspeed,
		double accel,
		double brake,
		double turn
) {
}
