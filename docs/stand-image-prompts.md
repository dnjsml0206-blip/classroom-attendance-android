# 스탠드 ON/OFF 이미지

내장 `image_gen` 편집 모드 사용. CLI/API 우회 및 코드로 이미지 픽셀 편집을 사용하지 않았다. 원본 그림은 유지하고 앱의 `drawable-nodpi`에 새 변형을 저장했다.

## 최종 저장 경로

- 낮 ON: `app/src/main/res/drawable-nodpi/hero_warm_stand_on.png`
- 낮 OFF: `app/src/main/res/drawable-nodpi/hero_warm_stand_off.png`
- 밤 ON: 기존 `app/src/main/res/drawable-nodpi/hero_night.png`
- 밤 OFF: `app/src/main/res/drawable-nodpi/hero_night_stand_off.png`

모두 1536 × 1024. `StandSceneView`가 같은 크롭 좌표로 표시하고 스탠드 위치의 투명 터치 영역만 동작한다.

## 사용한 프롬프트

### 밤 OFF — 입력: 기존 hero_night.png

Use case: lighting-weather. Edit target: the supplied nighttime classroom illustration, 1536x1024 landscape. Produce the identical composition with ONLY the existing desk lamp switched OFF. Keep its shade, bulb, stem, round base, plant, pencil cup, notebook, chairs, windows, moon and city in exactly the same positions and proportions. Remove the bulb's emitted yellow light, warm cone/glow and warm reflected pool from the nearby desk, cup, plant, notebook and window frame. Preserve cool blue ambient nighttime visibility; the lamp's unlit bulb should look gray/white and the fixture must remain easy to see. Do not darken the whole picture uniformly. No new objects, no lettering or UI. This is the OFF frame of an interactive two-state scene, so geometry must not shift.

### 낮 ON — 편집 대상: hero_warm.png, 스탠드 디자인 참고: hero_night.png

Use case: precise-object-edit. Image 1 is the edit target: preserve this warm daylight classroom desk illustration at 1536x1024 with the same framing, open doorway, notebook, potted plant and sunlight. Image 2 is a supporting reference ONLY for the curved-stem reading desk lamp design. Add one compact cream-colored reading desk lamp into the free tabletop space at the FAR RIGHT of image 1, to the right of and slightly behind the potted plant. Its shade/head should sit approximately within x=1260..1460, y=430..570, slender curved stem near x=1460 down to a compact base around x=1400,y=840. Keep the entire lamp inside frame. Make it naturally integrated with matching perspective and material, not a floating icon. The lamp is ON: a clearly glowing warm bulb in its shade and a modest warm pool of light on the table beneath it, visible despite daylight. Preserve the original background sunlight; do not change the notebook or plant placement. No other objects, text or UI. The lamp will be a touch target in the app, so its silhouette should be visible and distinct.

### 낮 OFF — 입력: 새 낮 ON 이미지

Use case: lighting-weather. Edit target: this exact 1536x1024 warm daylight classroom illustration with the cream desk lamp at the far right. Create the OFF-state frame: change ONLY the desk lamp's emitted light. Keep the lamp shade, white bulb, curved stem and round base absolutely fixed in the exact same positions and shapes. The bulb is not glowing; its underside is normal unlit cream/gray. Remove the lamp's warm glow from the adjacent door, plant and table, but retain the original natural sunlight and leaf shadows across the room and tabletop. The daytime scene must remain bright; do not uniformly dim, desaturate or move anything. Keep notebook, plant, doorway, camera framing and all geometry unchanged. No text, icons, controls or new objects. This will toggle against the ON frame in an app.
