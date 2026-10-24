import test from 'node:test';import assert from 'node:assert/strict';
import { normalizeOwnedImages } from '../src/utils/ownedImages.js';
test('historical owned uploads follow the authenticated storefront proxy without changing stored records',()=>{
  const id='12345678-1234-1234-1234-123456789abc.webp';
  const source={primaryImageUrl:`https://sg-mercato-backend-beta.duckdns.org/images/${id}`,other:'https://images.unsplash.com/photo-1',nested:[{image:`https://sg-mercato-backend.duckdns.org/images/${id}`} ]};
  const value=normalizeOwnedImages(source);assert.equal(value.primaryImageUrl,`/images/${id}`);assert.equal(value.nested[0].image,`/images/${id}`);assert.match(source.primaryImageUrl,/^https:/);assert.equal(value.other,source.other);
});
test('unowned URLs and unchanged payloads retain their identity',()=>{
  const source={url:'https://attacker.test/images/12345678-1234-1234-1234-123456789abc.webp',relative:'/images/image.webp'};
  assert.equal(normalizeOwnedImages(source),source);assert.equal(normalizeOwnedImages(null),null);
});
