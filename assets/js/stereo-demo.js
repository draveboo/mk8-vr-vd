/* =========================================================================
   VKXR — live stereo demo
   A hand-written WebGL2 scene of an iridescent "liquid crystal" body, with
   three ways to view it:
     single     one perspective camera, pointer-parallax
     both        the same scene rendered twice, eye-separated, side by side
     immersive  a real WebXR session, one viewport per eye from the viewer pose
   No libraries. Shaders, geometry and matrix maths are all inline.
   ========================================================================= */
(function () {
  'use strict';

  var stage = document.querySelector('[data-stereo-stage]');
  if (!stage) return;

  var canvas = stage.querySelector('canvas');
  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var gl = canvas.getContext('webgl2', {
    alpha: true,
    antialias: true,
    premultipliedAlpha: true,
    depth: true,
    powerPreference: 'high-performance'
  });

  if (!gl) {
    stage.setAttribute('data-webgl', 'unsupported');
    return;
  }
  stage.setAttribute('data-webgl', 'ready');

  /* ----------------------------- Shaders -------------------------------- */
  var VERT = [
    '#version 300 es',
    'precision highp float;',
    'in vec3 aPos;',
    'uniform mat4 uProj;',
    'uniform mat4 uView;',
    'uniform mat4 uModel;',
    'uniform float uTime;',
    'out vec3 vNormal;',
    'out vec3 vWorld;',
    'void main() {',
    '  // Smooth, slowly breathing displacement — the "liquid" in liquid crystal.',
    '  float w = sin(3.4 * aPos.x + uTime * 0.70)',
    '          * sin(2.9 * aPos.y - uTime * 0.52)',
    '          * sin(3.2 * aPos.z + uTime * 0.61);',
    '  vec3 p = aPos * (1.0 + 0.085 * w);',
    '  vec4 world = uModel * vec4(p, 1.0);',
    '  vWorld = world.xyz;',
    '  // Displacement is small and smooth, so the sphere normal stays a good',
    '  // approximation and we avoid recomputing normals per frame.',
    '  vNormal = mat3(uModel) * normalize(aPos);',
    '  gl_Position = uProj * uView * world;',
    '}'
  ].join('\n');

  var FRAG = [
    '#version 300 es',
    'precision highp float;',
    'in vec3 vNormal;',
    'in vec3 vWorld;',
    'uniform vec3 uCamPos;',
    'uniform float uTime;',
    'uniform vec3 uAccent;',
    'out vec4 outColor;',
    '',
    '// Analytic environment: a cool vertical ramp plus two soft light sources.',
    '// Cheaper and sharper than a cubemap, and it matches the site palette.',
    'vec3 env(vec3 d) {',
    '  float y = d.y * 0.5 + 0.5;',
    '  vec3 c = mix(vec3(0.015, 0.018, 0.024), vec3(0.52, 0.59, 0.70), pow(y, 1.7));',
    '  c += vec3(0.85, 0.92, 1.00) * pow(max(dot(d, normalize(vec3(0.45, 0.75, 0.40))), 0.0), 18.0) * 0.95;',
    '  c += uAccent * pow(max(dot(d, normalize(vec3(-0.60, 0.15, 0.50))), 0.0), 14.0) * 0.85;',
    '  return c;',
    '}',
    '',
    'void main() {',
    '  vec3 N = normalize(vNormal);',
    '  vec3 V = normalize(uCamPos - vWorld);',
    '  float fres = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0);',
    '',
    '  vec3 refl = env(reflect(-V, N));',
    '  vec3 refr = env(refract(-V, N, 0.68));',
    '',
    '  // Thin-film interference: hue driven by view angle, drifting slowly.',
    '  vec3 irid = 0.5 + 0.5 * cos(6.28318 * (vec3(0.0, 0.33, 0.67) + fres * 1.55 + uTime * 0.06));',
    '',
    '  vec3 col = mix(refr * 0.72, refl * 0.92, fres);',
    '  col = mix(col, irid * (0.45 + 0.95 * fres), 0.55);',
    '',
    '  vec3 H = normalize(V + normalize(vec3(0.40, 0.82, 0.35)));',
    '  col += pow(max(dot(N, H), 0.0), 130.0) * 1.7;',
    '  col += uAccent * fres * 0.28;',
    '  col += pow(fres, 3.0) * 0.10;',
    '',
    '  outColor = vec4(col, 1.0);',
    '}'
  ].join('\n');

  function compile(type, src) {
    var s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      if (window.console) console.error('[stereo-demo] shader:', gl.getShaderInfoLog(s));
      return null;
    }
    return s;
  }

  var vs = compile(gl.VERTEX_SHADER, VERT);
  var fs = compile(gl.FRAGMENT_SHADER, FRAG);
  if (!vs || !fs) { stage.setAttribute('data-webgl', 'unsupported'); return; }

  var prog = gl.createProgram();
  gl.attachShader(prog, vs);
  gl.attachShader(prog, fs);
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    if (window.console) console.error('[stereo-demo] link:', gl.getProgramInfoLog(prog));
    stage.setAttribute('data-webgl', 'unsupported');
    return;
  }

  var uProj = gl.getUniformLocation(prog, 'uProj');
  var uView = gl.getUniformLocation(prog, 'uView');
  var uModel = gl.getUniformLocation(prog, 'uModel');
  var uCamPos = gl.getUniformLocation(prog, 'uCamPos');
  var uTime = gl.getUniformLocation(prog, 'uTime');
  var uAccent = gl.getUniformLocation(prog, 'uAccent');
  var aPos = gl.getAttribLocation(prog, 'aPos');

  /* --------------------------- Geometry --------------------------------- */
  /* Subdivided icosahedron, emitted non-indexed with smooth sphere normals. */
  function buildCrystal(subdiv) {
    var t = (1 + Math.sqrt(5)) / 2;
    var v = [
      [-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0],
      [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t],
      [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]
    ].map(function (p) { return norm(p); });

    var f = [
      [0, 11, 5], [0, 5, 1], [0, 1, 7], [0, 7, 10], [0, 10, 11],
      [1, 5, 9], [5, 11, 4], [11, 10, 2], [10, 7, 6], [7, 1, 8],
      [3, 9, 4], [3, 4, 2], [3, 2, 6], [3, 6, 8], [3, 8, 9],
      [4, 9, 5], [2, 4, 11], [6, 2, 10], [8, 6, 7], [9, 8, 1]
    ];

    for (var s = 0; s < subdiv; s++) {
      var next = [];
      for (var i = 0; i < f.length; i++) {
        var a = f[i][0], b = f[i][1], c = f[i][2];
        var ab = v.length; v.push(norm(mid(v[a], v[b])));
        var bc = v.length; v.push(norm(mid(v[b], v[c])));
        var ca = v.length; v.push(norm(mid(v[c], v[a])));
        next.push([a, ab, ca], [b, bc, ab], [c, ca, bc], [ab, bc, ca]);
      }
      f = next;
    }

    var data = new Float32Array(f.length * 9);
    var k = 0;
    for (var j = 0; j < f.length; j++) {
      for (var e = 0; e < 3; e++) {
        var p = v[f[j][e]];
        data[k++] = p[0]; data[k++] = p[1]; data[k++] = p[2];
      }
    }
    return data;
  }
  function norm(p) { var l = Math.hypot(p[0], p[1], p[2]) || 1; return [p[0] / l, p[1] / l, p[2] / l]; }
  function mid(a, b) { return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2]; }

  var crystalData = buildCrystal(3);
  var vertCount = crystalData.length / 3;

  var vbo = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
  gl.bufferData(gl.ARRAY_BUFFER, crystalData, gl.STATIC_DRAW);

  /* --------------------------- Stage grid -------------------------------- */
  /* A ground plane. Without a spatial reference a lone object in a headset reads
     as a flat picture — depth needs something to be in front of. Screen-space
     derivatives (fwidth) keep the lines crisp at any distance. */
  var GRID_VERT = [
    '#version 300 es',
    'precision highp float;',
    'in vec3 aPos;',
    'uniform mat4 uProj;',
    'uniform mat4 uView;',
    'out vec3 vWorld;',
    'void main() {',
    '  vWorld = aPos;',
    '  gl_Position = uProj * uView * vec4(aPos, 1.0);',
    '}'
  ].join('\n');

  var GRID_FRAG = [
    '#version 300 es',
    'precision highp float;',
    'in vec3 vWorld;',
    'uniform vec3 uAccent;',
    'uniform float uFade;',
    'out vec4 outColor;',
    'float lineMask(vec2 p, float scale) {',
    '  vec2 q = p / scale;',
    '  vec2 g = abs(fract(q - 0.5) - 0.5) / max(fwidth(q), vec2(1e-5));',
    '  return 1.0 - min(min(g.x, g.y), 1.0);',
    '}',
    'void main() {',
    '  vec2 p = vWorld.xz;',
    '  float m = lineMask(p, 1.0) * 0.30 + lineMask(p, 5.0) * 0.70;',
    '  float fade = 1.0 - smoothstep(3.0, 20.0, length(p));',
    '  float a = m * fade * uFade;',
    '  if (a < 0.003) discard;',
    '  outColor = vec4(mix(vec3(0.45, 0.58, 0.66), uAccent, 0.45), a);',
    '}'
  ].join('\n');

  var gridProg = null;
  var gridVbo = null;
  var gu = {};

  var gvs = compile(gl.VERTEX_SHADER, GRID_VERT);
  var gfs = compile(gl.FRAGMENT_SHADER, GRID_FRAG);
  if (gvs && gfs) {
    gridProg = gl.createProgram();
    gl.attachShader(gridProg, gvs);
    gl.attachShader(gridProg, gfs);
    gl.linkProgram(gridProg);
    if (gl.getProgramParameter(gridProg, gl.LINK_STATUS)) {
      gu.proj = gl.getUniformLocation(gridProg, 'uProj');
      gu.view = gl.getUniformLocation(gridProg, 'uView');
      gu.accent = gl.getUniformLocation(gridProg, 'uAccent');
      gu.fade = gl.getUniformLocation(gridProg, 'uFade');
      gu.pos = gl.getAttribLocation(gridProg, 'aPos');

      var gy = -1.5, gs = 26;
      var quad = new Float32Array([
        -gs, gy, -gs, gs, gy, -gs, gs, gy, gs,
        -gs, gy, -gs, gs, gy, gs, -gs, gy, gs
      ]);
      gridVbo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, gridVbo);
      gl.bufferData(gl.ARRAY_BUFFER, quad, gl.STATIC_DRAW);
    } else {
      gridProg = null;
    }
  }

  /* A simple world-space glass page in front of the crystal during XR. */
  var glassProg = null, glassVbo = null, glassU = {};
  var glvs = compile(gl.VERTEX_SHADER, [
    '#version 300 es',
    'precision highp float;',
    'in vec3 aPos;',
    'uniform mat4 uProj;',
    'uniform mat4 uView;',
    'out vec2 vUv;',
    'void main() {',
    '  vUv = aPos.xy * vec2(0.5, 0.5) + 0.5;',
    '  gl_Position = uProj * uView * vec4(aPos, 1.0);',
    '}'
  ].join('\n'));
  var glfs = compile(gl.FRAGMENT_SHADER, [
    '#version 300 es',
    'precision highp float;',
    'in vec2 vUv;',
    'out vec4 outColor;',
    'void main() {',
    '  float edge = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));',
    '  float rim = 1.0 - smoothstep(0.0, 0.018, edge);',
    '  vec3 tint = mix(vec3(0.035, 0.045, 0.060), vec3(0.10, 0.14, 0.17), vUv.y);',
    '  outColor = vec4(tint + rim * 0.12, 0.28 + rim * 0.16);',
    '}'
  ].join('\n'));
  if (glvs && glfs) {
    glassProg = gl.createProgram();
    gl.attachShader(glassProg, glvs);
    gl.attachShader(glassProg, glfs);
    gl.linkProgram(glassProg);
    if (gl.getProgramParameter(glassProg, gl.LINK_STATUS)) {
      glassU.proj = gl.getUniformLocation(glassProg, 'uProj');
      glassU.view = gl.getUniformLocation(glassProg, 'uView');
      glassU.pos = gl.getAttribLocation(glassProg, 'aPos');
      glassVbo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, glassVbo);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
        -1.7, -1.12, -2.15,  1.7, -1.12, -2.15,  1.7, 1.12, -2.15,
        -1.7, -1.12, -2.15,  1.7,  1.12, -2.15, -1.7, 1.12, -2.15
      ]), gl.STATIC_DRAW);
    } else {
      glassProg = null;
    }
  }

  /* --------------------------- Matrix maths ----------------------------- */
  function perspective(fovy, aspect, near, far) {
    var f = 1 / Math.tan(fovy / 2), nf = 1 / (near - far);
    return new Float32Array([
      f / aspect, 0, 0, 0,
      0, f, 0, 0,
      0, 0, (far + near) * nf, -1,
      0, 0, 2 * far * near * nf, 0
    ]);
  }

  function lookAt(eye, center, up) {
    var z = norm3(sub3(eye, center));
    var x = norm3(cross(up, z));
    var y = cross(z, x);
    return new Float32Array([
      x[0], y[0], z[0], 0,
      x[1], y[1], z[1], 0,
      x[2], y[2], z[2], 0,
      -dot3(x, eye), -dot3(y, eye), -dot3(z, eye), 1
    ]);
  }
  function sub3(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function dot3(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  function norm3(v) { var l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l]; }

  function mul(a, b) {
    var o = new Float32Array(16);
    for (var c = 0; c < 4; c++) {
      for (var r = 0; r < 4; r++) {
        o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
      }
    }
    return o;
  }
  function rotY(a) { var c = Math.cos(a), s = Math.sin(a); return new Float32Array([c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]); }
  function rotX(a) { var c = Math.cos(a), s = Math.sin(a); return new Float32Array([1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]); }

  /* ----------------------------- State ---------------------------------- */
  var mode = 'single';
  var ipd = 0.085;          // stereo separation, world units
  var spin = 0, spinVel = 0.16;
  var tiltX = 0, tiltY = 0, targetTiltX = 0, targetTiltY = 0;
  var dragging = false, lastX = 0, lastY = 0;
  var dpr = 1;

  /* --------------------------- Resize ----------------------------------- */
  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    var w = Math.max(1, Math.round(stage.clientWidth * dpr));
    var h = Math.max(1, Math.round(stage.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
  }
  if (window.ResizeObserver) new ResizeObserver(resize).observe(stage);
  window.addEventListener('resize', resize);
  resize();

  /* ---------------------------- Interaction ----------------------------- */
  stage.addEventListener('pointermove', function (e) {
    if (dragging) {
      spinVel += (e.clientX - lastX) * 0.00042;
      targetTiltX = clamp(targetTiltX + (e.clientY - lastY) * 0.0022, -0.7, 0.7);
      lastX = e.clientX; lastY = e.clientY;
      return;
    }
    var r = stage.getBoundingClientRect();
    targetTiltY = ((e.clientX - r.left) / r.width - 0.5) * 0.9;
    targetTiltX = ((e.clientY - r.top) / r.height - 0.5) * 0.7;
  });
  stage.addEventListener('pointerleave', function () { targetTiltX = 0; targetTiltY = 0; });
  stage.addEventListener('pointerdown', function (e) {
    dragging = true; lastX = e.clientX; lastY = e.clientY;
    stage.setPointerCapture && stage.setPointerCapture(e.pointerId);
    stage.setAttribute('data-dragging', 'true');
  });
  function endDrag(e) {
    dragging = false;
    stage.removeAttribute('data-dragging');
    if (e && stage.releasePointerCapture && e.pointerId != null) {
      try { stage.releasePointerCapture(e.pointerId); } catch (err) { /* already released */ }
    }
  }
  stage.addEventListener('pointerup', endDrag);
  stage.addEventListener('pointercancel', endDrag);
  function clamp(v, a, b) { return Math.min(b, Math.max(a, v)); }

  /* ------------------------------ Draw ---------------------------------- */
  var accent = [0.604, 0.851, 0.910];   // --accent #9ad9e8

  function drawView(proj, view, camPos, time, depth) {
    var model = mul(rotX(tiltX), rotY(spin + tiltY));
    if (depth != null) model[14] = depth;
    gl.uniformMatrix4fv(uProj, false, proj);
    gl.uniformMatrix4fv(uView, false, view);
    gl.uniformMatrix4fv(uModel, false, model);
    gl.uniform3fv(uCamPos, camPos);
    gl.uniform1f(uTime, time);
    gl.uniform3fv(uAccent, accent);
    gl.drawArrays(gl.TRIANGLES, 0, vertCount);
  }

  /* Blended, drawn after the crystal, then restores the crystal program and
     attribute bindings so the next eye's pass starts clean. */
  function drawGrid(proj, view, fade) {
    if (!gridProg) return;
    gl.useProgram(gridProg);
    gl.uniformMatrix4fv(gu.proj, false, proj);
    gl.uniformMatrix4fv(gu.view, false, view);
    gl.uniform3fv(gu.accent, accent);
    gl.uniform1f(gu.fade, fade);
    gl.bindBuffer(gl.ARRAY_BUFFER, gridVbo);
    gl.enableVertexAttribArray(gu.pos);
    gl.vertexAttribPointer(gu.pos, 3, gl.FLOAT, false, 0, 0);

    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    /* The quad's winding faces down when seen from above, so back-face culling
       would discard it. Culling buys nothing for a single flat plane anyway. */
    gl.disable(gl.CULL_FACE);
    gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    gl.depthMask(true);
    gl.enable(gl.CULL_FACE);
    gl.disable(gl.BLEND);

    gl.useProgram(prog);
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 0, 0);
  }

  function drawGlass(proj, view) {
    if (!glassProg) return;
    gl.useProgram(glassProg);
    gl.uniformMatrix4fv(glassU.proj, false, proj);
    gl.uniformMatrix4fv(glassU.view, false, view);
    gl.bindBuffer(gl.ARRAY_BUFFER, glassVbo);
    gl.enableVertexAttribArray(glassU.pos);
    gl.vertexAttribPointer(glassU.pos, 3, gl.FLOAT, false, 0, 0);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.disable(gl.CULL_FACE);
    gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    gl.depthMask(true);
    gl.enable(gl.CULL_FACE);
    gl.disable(gl.BLEND);
  }

  function beginFrame() {
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);
    gl.enable(gl.CULL_FACE);
    gl.useProgram(prog);
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 0, 0);
  }

  function renderDesktop(time) {
    resize();
    beginFrame();

    var aspect = canvas.width / Math.max(1, canvas.height);
    var dist = 3.05;

    if (mode === 'both') {
      // Two viewports, cameras separated on the world X axis — the same trick
      // a headset uses, just laid out flat instead of one per eye.
      var half = canvas.width / 2;
      for (var eye = 0; eye < 2; eye++) {
        var sign = eye === 0 ? -1 : 1;
        gl.viewport(eye * half, 0, half, canvas.height);
        var proj = perspective(Math.PI / 4, (half) / Math.max(1, canvas.height), 0.1, 100);
        var cam = [sign * ipd * 0.5, 0.12, dist];
        var view = lookAt(cam, [0, 0, 0], [0, 1, 0]);
        drawView(proj, view, cam, time);
        drawGrid(proj, view, 0.55);
      }
    } else {
      var p = perspective(Math.PI / 3.4, aspect, 0.1, 100);
      var c = [0, 0.1, dist];
      var v = lookAt(c, [0, 0, 0], [0, 1, 0]);
      drawView(p, v, c, time);
      drawGrid(p, v, 0.55);
    }
  }

  /* ------------------------------- Loop --------------------------------- */
  var t0 = performance.now();
  var animating = false;
  var onScreen = true;

  function frame(now) {
    if (!animating) return;
    if (xrSession) { animating = false; return; }
    requestAnimationFrame(frame);

    var time = (now - t0) / 1000;
    spinVel += (0.16 - spinVel) * (dragging ? 0 : 0.02);
    spin += spinVel;
    tiltX += (targetTiltX - tiltX) * 0.06;
    tiltY += (targetTiltY - tiltY) * 0.06;
    renderDesktop(time);
  }

  function startLoop() {
    if (animating || xrSession || !onScreen || document.hidden) return;
    if (reduceMotion) { renderDesktop(0); return; }   // one static frame
    animating = true;
    requestAnimationFrame(frame);
  }

  /* This is the only continuously animating thing on the page, so it must not
     burn a GPU while it is scrolled out of view. */
  if (window.IntersectionObserver) {
    new IntersectionObserver(function (entries) {
      onScreen = entries[0].isIntersecting;
      if (onScreen) startLoop();
      else animating = false;
    }, { threshold: 0.01 }).observe(stage);
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) animating = false;
    else startLoop();
  });

  startLoop();

  /* ------------------------------- WebXR -------------------------------- */
  var xrSession = null, xrRefSpace = null;
  var vrBtn = document.querySelector('[data-xr-enter]');
  var xrStatus = document.querySelector('[data-xr-status]');

  function setStatus(text) { if (xrStatus) xrStatus.textContent = text; }

  function onXRFrame(time, frame) {
    if (!xrSession) return;
    xrSession.requestAnimationFrame(onXRFrame);

    var pose = frame.getViewerPose(xrRefSpace);
    if (!pose) return;

    var layer = xrSession.renderState.baseLayer;
    gl.bindFramebuffer(gl.FRAMEBUFFER, layer.framebuffer);
    gl.clearColor(0, 0, 0, 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);
    gl.enable(gl.CULL_FACE);
    gl.useProgram(prog);
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 3, gl.FLOAT, false, 0, 0);

    var t = time / 1000;
    if (!reduceMotion) spin += spinVel * 0.5;

    for (var i = 0; i < pose.views.length; i++) {
      var view = pose.views[i];
      var vp = layer.getViewport(view);
      gl.viewport(vp.x, vp.y, vp.width, vp.height);
      var m = view.transform.matrix;
      drawView(view.projectionMatrix, view.transform.inverse.matrix, [m[12], m[13], m[14]], t, -3.2);
      drawGrid(view.projectionMatrix, view.transform.inverse.matrix, 0.85);
      drawGlass(view.projectionMatrix, view.transform.inverse.matrix);
    }
  }

  function endXR() {
    xrSession = null;
    xrRefSpace = null;
    stage.removeAttribute('data-xr');
    if (vrBtn) vrBtn.removeAttribute('disabled');
    setStatus('Session ended');
    startLoop();
  }

  function enterXR() {
    if (!navigator.xr) return;
    navigator.xr.requestSession('immersive-vr', {
      optionalFeatures: ['local-floor', 'bounded-floor', 'medium-fixed-foveation-level']
    })
      .then(function (session) {
        xrSession = session;
        var layer = new window.XRWebGLLayer(session, gl);
        // Fixed foveated rendering: the periphery of this scene is mostly empty
        // background, so the quality cost is invisible while the fill-rate
        // saving is real. Set as an attribute too, for runtimes that expose the
        // control without honouring the session feature.
        if ('fixedFoveation' in layer) {
          try { layer.fixedFoveation = 0.4; } catch (err) { /* not supported */ }
        }
        session.updateRenderState({ baseLayer: layer });
        return session.requestReferenceSpace('local').then(function (ref) {
          xrRefSpace = ref;
          stage.setAttribute('data-xr', 'active');
          animating = false;
          setStatus('In headset — take a look around');
          session.addEventListener('end', endXR);
          session.requestAnimationFrame(onXRFrame);
        });
      })
      .catch(function (err) {
        setStatus('Could not start a headset session');
        if (window.console) console.warn('[stereo-demo] xr:', err && err.message);
      });
  }

  if (vrBtn) {
    if (!navigator.xr) {
      vrBtn.setAttribute('disabled', 'disabled');
      setStatus('No headset support in this browser');
    } else {
      navigator.xr.isSessionSupported('immersive-vr').then(function (ok) {
        if (ok) {
          stage.setAttribute('data-xr-ready', 'true');
          vrBtn.removeAttribute('disabled');
          setStatus('Headset detected — ready');
        } else {
          vrBtn.setAttribute('disabled', 'disabled');
          setStatus('No headset detected');
        }
      }).catch(function () {
        vrBtn.setAttribute('disabled', 'disabled');
        setStatus('No headset detected');
      });
    }
    vrBtn.addEventListener('click', function () {
      if (!xrSession) enterXR();
      else xrSession.end();
    });
  }

  /* ---------------------------- Mode controls --------------------------- */
  var bothBtn = document.querySelector('[data-stereo-both]');
  var sep = document.querySelector('[data-stereo-sep]');
  var sepOut = document.querySelector('[data-stereo-sep-out]');

  function syncBoth() {
    stage.setAttribute('data-mode', mode);
    if (bothBtn) {
      bothBtn.setAttribute('aria-pressed', String(mode === 'both'));
      bothBtn.querySelector('span').textContent = mode === 'both' ? 'Both eyes: on' : 'Both eyes';
    }
  }
  if (bothBtn) {
    bothBtn.addEventListener('click', function () {
      mode = mode === 'both' ? 'single' : 'both';
      syncBoth();
    });
  }
  syncBoth();

  if (sep) {
    sep.addEventListener('input', function () {
      ipd = Number(sep.value) / 1000;
      if (sepOut) sepOut.textContent = Number(sep.value).toFixed(0) + 'mm';
    });
  }

  var resetBtn = document.querySelector('[data-stereo-reset]');
  if (resetBtn) {
    resetBtn.addEventListener('click', function () {
      spinVel = 0.16; spin = 0; tiltX = tiltY = targetTiltX = targetTiltY = 0;
      mode = 'single'; syncBoth();
      if (sep) { sep.value = '85'; ipd = 0.085; if (sepOut) sepOut.textContent = '85mm'; }
    });
  }
})();
