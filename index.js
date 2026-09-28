module.exports = (req, res) => {
  return {
    statusCode: 200,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: 'ChaseKeno Application Ready' })
  };
};