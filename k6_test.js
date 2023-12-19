import http from 'k6/http';
import {sleep} from 'k6';

export const options = {
  stages: [
    { duration: '60s', target: 200 },
    { duration: '60s', target: 200 },
    { duration: '60s', target: 0 },
  ],
};

export default () => {
  const urlRes = http.get('http://127.0.0.1:8080/hello');
  sleep(0.5);
};