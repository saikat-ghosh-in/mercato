export function profileImageSource(value) {
  return typeof value === 'string' && /^(https?:\/\/|\/images\/)/i.test(value) ? value : null;
}
